package service

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/armanlife/arman-vpn-client/core/internal/fxa"
)

func TestSignInVerificationCountsOnlyRejectedCodes(t *testing.T) {
	type reply struct {
		status     int
		body       string
		err        error
		code, text string
	}
	wrongEmail := reply{status: http.StatusBadRequest, body: `{"errno":183}`, code: signInWrongCode}
	wrongTOTP := reply{status: http.StatusOK, body: `{"success":false}`, code: signInWrongTOTP}
	network := reply{err: io.ErrUnexpectedEOF, code: signInNetwork}
	cases := []struct {
		name, method, path, step string
		replies                  []reply
		finalStep                string
	}{
		{
			name: "email code retries with the same session", method: "email-2fa",
			path: "/v1/session/verify_code", step: stepEmailCode, finalStep: stepDone,
			replies: []reply{
				wrongEmail, wrongEmail, wrongEmail, wrongEmail,
				{status: http.StatusNotAcceptable, code: signInBlocked, text: "security check blocked code verification"},
				network,
				{status: http.StatusServiceUnavailable, text: "could not verify the code right now"},
				{status: http.StatusBadRequest, body: `{"errno":107}`, text: "Enter only the digits"},
				{status: http.StatusOK, body: `{}`},
			},
		},
		{
			name: "TOTP rejection reaches the limit across a network failure", method: "totp-2fa",
			path: "/v1/session/verify/totp", step: stepTOTP, finalStep: stepFailed,
			replies: []reply{
				wrongTOTP, wrongTOTP, wrongTOTP, wrongTOTP, network,
				{status: http.StatusOK, body: `{"success":false}`, code: signInTooManyCodes},
			},
		},
		{
			name: "Mozilla throttle stops verification", method: "email-2fa",
			path: "/v1/session/verify_code", step: stepEmailCode, finalStep: stepFailed,
			replies: []reply{{status: http.StatusTooManyRequests, code: signInThrottled}},
		},
		{
			name: "invalid session stops verification", method: "email-2fa",
			path: "/v1/session/verify_code", step: stepEmailCode, finalStep: stepFailed,
			replies: []reply{{status: http.StatusUnauthorized, body: `{"errno":110,"message":"Invalid authentication token"}`, text: "Invalid authentication token"}},
		},
		{
			name: "missing TOTP result is a response error", method: "totp-2fa",
			path: "/v1/session/verify/totp", step: stepTOTP, finalStep: stepFailed,
			replies: []reply{{status: http.StatusOK, body: `{}`, text: "verification response is missing success"}},
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			var calls atomic.Int32
			setCoreControlPlane(t, func(r *http.Request) (*http.Response, error) {
				if r.Method == http.MethodGet && r.URL.Path == "/" {
					return coreResponse(r, http.StatusOK, "no challenge page", nil), nil
				}
				if r.Method != http.MethodPost || r.URL.Path != tc.path {
					return nil, fmt.Errorf("unexpected verification request %s %s", r.Method, r.URL.Path)
				}
				n := int(calls.Add(1))
				if n > len(tc.replies) {
					return nil, fmt.Errorf("unexpected verification attempt %d", n)
				}
				reply := tc.replies[n-1]
				if reply.err != nil {
					return nil, reply.err
				}
				return coreResponse(r, reply.status, reply.body, nil), nil
			})

			ctx, cancel := context.WithCancel(context.Background())
			s := &signIn{input: make(chan string)}
			done := make(chan struct{})
			go func() {
				defer close(done)
				resp := &fxa.LoginResponse{SessionToken: strings.Repeat("ab", 32), VerificationMethod: tc.method}
				s.finish("new@example.com", "test-account", s.verify(ctx, "new@example.com", resp))
			}()
			t.Cleanup(func() {
				cancel()
				<-done
			})

			waitStep := func(step string, want reply, n int) {
				t.Helper()
				waitForCondition(t, 2*time.Second, func() bool {
					if int(calls.Load()) != n {
						return false
					}
					view := s.snapshot()
					return view.Step == step && view.ErrorCode == want.code && strings.Contains(view.Error, want.text)
				})
			}
			waitStep(tc.step, reply{}, 0)
			for i, want := range tc.replies {
				if err := s.answer("123456"); err != nil {
					t.Fatalf("submission %d: %v", i+1, err)
				}
				step := tc.step
				if i == len(tc.replies)-1 {
					step = tc.finalStep
				}
				waitStep(step, want, i+1)
			}
		})
	}
}

func TestStartBrowserValidation(t *testing.T) {
	r := &signInRegistry{}
	token := strings.Repeat("ab", 32)
	if _, err := r.startBrowser("", token); err == nil {
		t.Fatal("empty email was accepted")
	}
	if _, err := r.startBrowser("person@example.com", "not-a-token"); err == nil {
		t.Fatal("invalid session token was accepted")
	}
}

func TestNormalizeSessionToken(t *testing.T) {
	want := strings.Repeat("ab", 32)
	got, err := normalizeSessionToken("  " + strings.ToUpper(want) + "  ")
	if err != nil {
		t.Fatal(err)
	}
	if got != want {
		t.Fatalf("token = %q, want normalized token", got)
	}
	if _, err := normalizeSessionToken(strings.Repeat("g", 64)); err == nil {
		t.Fatal("non-hex session token was accepted")
	}
}

func TestWaitSessionConfirmed(t *testing.T) {
	var calls atomic.Int32
	setCoreControlPlane(t, func(r *http.Request) (*http.Response, error) {
		if r.Method != http.MethodGet || r.URL.Path != "/v1/recovery_email/status" {
			return nil, fmt.Errorf("unexpected session status request %s %s", r.Method, r.URL.Path)
		}
		if got := r.Header.Get("Accept-Encoding"); got != "identity" {
			return nil, fmt.Errorf("Accept-Encoding = %q, want identity", got)
		}
		verified := calls.Add(1) > 1
		body := `{"verified":false}`
		if verified {
			body = `{"verified":true}`
		}
		return coreResponse(r, http.StatusOK, body, nil), nil
	})
	previousPoll := browserConfirmPoll
	browserConfirmPoll = time.Millisecond
	t.Cleanup(func() { browserConfirmPoll = previousPoll })

	s := &signIn{}
	ctx, cancel := context.WithTimeout(context.Background(), time.Second)
	defer cancel()
	if err := s.waitSessionConfirmed(ctx, strings.Repeat("ab", 32)); err != nil {
		t.Fatalf("waitSessionConfirmed: %v", err)
	}
	if got := calls.Load(); got != 2 {
		t.Fatalf("status checks = %d, want 2", got)
	}
	view := s.snapshot()
	if view.Step != stepWorking || !strings.Contains(view.Message, "confirmed") {
		t.Fatalf("waiting state = %#v", view)
	}
}

func TestWaitSessionConfirmedHonorsCancellation(t *testing.T) {
	setCoreControlPlane(t, func(r *http.Request) (*http.Response, error) {
		return nil, context.Canceled
	})
	s := &signIn{}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if err := s.waitSessionConfirmed(ctx, strings.Repeat("ab", 32)); !errors.Is(err, context.Canceled) {
		t.Fatalf("waitSessionConfirmed error = %v, want context canceled", err)
	}
}
