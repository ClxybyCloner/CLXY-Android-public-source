package service

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestBrowserSignInLinkRoutesAreRemoved(t *testing.T) {
	d := newTestDaemon(t)
	d.localToken = "phone-token"
	d.RequireLocalToken()
	handler := newDashboardServer(d).handler()

	apiReq := httptest.NewRequest(http.MethodPost, "/api/session/link", strings.NewReader("{}"))
	apiReq.Host = "127.0.0.1:10820"
	apiReq.Header.Set("Content-Type", "application/json")
	apiReq.Header.Set(localTokenHeader, "phone-token")
	apiRec := httptest.NewRecorder()
	handler.ServeHTTP(apiRec, apiReq)
	if apiRec.Code != http.StatusNotFound {
		t.Fatalf("browser sign-in link API still exists: %d %s", apiRec.Code, apiRec.Body.String())
	}

	pageReq := httptest.NewRequest(http.MethodGet, "/app/login/unused", nil)
	pageReq.Host = "127.0.0.1:10820"
	pageRec := httptest.NewRecorder()
	handler.ServeHTTP(pageRec, pageReq)
	if pageRec.Code != http.StatusNotFound {
		t.Fatalf("browser sign-in page still exists: %d", pageRec.Code)
	}
}

func TestTokenOnlyAPI(t *testing.T) {
	d := newTestDaemon(t)
	d.localToken = "phone-token"
	d.RequireLocalToken()
	srv := newDashboardServer(d)
	handler := srv.handler()
	call := func(method, path, token string, cookie *http.Cookie) *httptest.ResponseRecorder {
		body := strings.NewReader("")
		if method != http.MethodGet {
			body = strings.NewReader("{}")
		}
		req := httptest.NewRequest(method, path, body)
		req.Host = "127.0.0.1:10820"
		if method != http.MethodGet {
			req.Header.Set("Content-Type", "application/json")
		}
		if token != "" {
			req.Header.Set(localTokenHeader, token)
		}
		if cookie != nil {
			req.AddCookie(cookie)
		}
		rec := httptest.NewRecorder()
		handler.ServeHTTP(rec, req)
		return rec
	}

	if rec := call(http.MethodGet, "/api/accounts", "", nil); rec.Code != http.StatusUnauthorized {
		t.Fatalf("another app reached the API without the token: %d", rec.Code)
	}
	if rec := call(http.MethodGet, "/api/accounts", "phone-token", nil); rec.Code != http.StatusOK {
		t.Fatalf("the app's token must open the API: %d", rec.Code)
	}
	if rec := call(http.MethodPost, "/api/session", "", nil); rec.Code != http.StatusUnauthorized {
		t.Fatalf("without a dashboard login, a password login must not sign a browser in: %d", rec.Code)
	}
	var info struct {
		Required bool `json:"login_required"`
	}
	rec := call(http.MethodGet, "/api/session", "", nil)
	if json.Unmarshal(rec.Body.Bytes(), &info) != nil || !info.Required {
		t.Fatalf("the page must ask for a sign-in: %s", rec.Body.String())
	}
}
