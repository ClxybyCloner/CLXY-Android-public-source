import 'package:flutter/widgets.dart';

/// English and Persian copy for the Android-only hotspot proxy screen.
/// Other app languages fall back to English until this screen is translated.
class ProxyProfileCopy {
  const ProxyProfileCopy._(this._persian);

  factory ProxyProfileCopy.of(BuildContext context) =>
      ProxyProfileCopy._(Localizations.localeOf(context).languageCode == 'fa');

  final bool _persian;

  String get title => _text('Hotspot proxy', 'پروکسی هات‌اسپات');
  String get intro => _text(
    'Connect your iPhone to this hotspot. Use the HTTP port in Wi-Fi proxy settings. In Telegram, add a SOCKS5 proxy with the separate port below.',
    'آیفون را به هات‌اسپات این گوشی وصل کن. برای Proxy وای‌فای از پورت HTTP استفاده کن. در تلگرام هم یک SOCKS5 با پورت جداگانهٔ پایین اضافه کن.',
  );
  String get sharingTitle => _text(
    'HTTP and SOCKS5 hotspot proxies',
    'پراکسی‌های HTTP و SOCKS5 هات‌اسپات',
  );
  String get unavailableTitle =>
      _text('Hotspot sharing', 'اشتراک‌گذاری هات‌اسپات');
  String get unavailableInTestBuild => _text(
    'Hotspot sharing isn’t available in this test build.',
    'اشتراک‌گذاری هات‌اسپات در این نسخهٔ آزمایشی در دسترس نیست.',
  );
  String get sharingToggle =>
      _text('Turn on hotspot proxies', 'روشن‌کردن پراکسی‌های هات‌اسپات');
  String get sharingHint => _text(
    'Both listen only on the selected private IP.',
    'هر دو فقط روی IP خصوصی انتخاب‌شده گوش می‌دهند.',
  );
  String get advancedSettings => _text('Advanced', 'پیشرفته');
  String get networkInterface => _text('Hotspot address', 'آدرس هات‌اسپات');
  String get refreshAddresses => _text('Refresh', 'تازه‌سازی');
  String get noAddresses => _text(
    'No hotspot IP found. Turn on this phone’s hotspot, then refresh.',
    'IP هات‌اسپات پیدا نشد. هات‌اسپات این گوشی را روشن و فهرست را تازه کن.',
  );
  String get bindSelected => _text('Apply address', 'اعمال آدرس');
  String get allInterfacesWarning => _text(
    'The proxies are limited to one selected private IP.',
    'پراکسی‌ها فقط روی یک IP خصوصی انتخاب‌شده در دسترس‌اند.',
  );
  String get shareClientHint => _text(
    'iPhone: join this hotspot, then open Settings > Wi-Fi > ⓘ > Configure Proxy > Manual. Enter Server and HTTP port. Leave Authentication off.',
    'آیفون: به هات‌اسپات وصل شو، بعد Settings > Wi-Fi > ⓘ > Configure Proxy > Manual را باز کن. Server و پورت HTTP را وارد کن و Authentication را خاموش بگذار.',
  );
  String get telegramHint => _text(
    'Telegram: open Settings > Data and Storage > Proxy > Add Proxy > SOCKS5. Enter the same Server and the SOCKS5 port. Leave username and password empty.',
    'تلگرام: Settings > Data and Storage > Proxy > Add Proxy > SOCKS5 را باز کن. همان Server و پورت SOCKS5 را وارد کن و نام کاربری و گذرواژه را خالی بگذار.',
  );
  String get shareLimits => _text(
    'The Wi-Fi HTTP setting only affects apps that use it. SOCKS5 carries TCP CONNECT, not UDP. Through this service, destination ports are limited to 80, 443, 8080 and 8443; direct routes follow your Routing settings. Telegram calls or connections on other ports may not work.',
    'تنظیم HTTP وای‌فای فقط روی برنامه‌هایی اثر دارد که از آن استفاده می‌کنند. SOCKS5 فقط TCP CONNECT را عبور می‌دهد و UDP را پشتیبانی نمی‌کند. در این سرویس فقط اتصال به پورت‌های ۸۰، ۴۴۳، ۸۰۸۰ و ۸۴۴۳ پذیرفته می‌شود؛ مسیر مستقیم طبق تنظیمات Routing است. تماس تلگرام یا اتصال روی پورت‌های دیگر ممکن است کار نکند.',
  );
  String get coreMustRun => _text(
    'Keep CLXY connected while the other device uses these proxies.',
    'تا وقتی دستگاه دیگر از این پراکسی‌ها استفاده می‌کند، CLXY را متصل نگه دار.',
  );
  String get sharingSaved =>
      _text('Hotspot proxies updated', 'پراکسی‌های هات‌اسپات به‌روز شدند');
  String get invalidBindAddress => _text(
    'Choose a private IPv4 address assigned to this phone.',
    'یک IP خصوصی IPv4 متعلق به همین گوشی انتخاب کن.',
  );
  String get manualProfile => _text('iPhone setup', 'تنظیم آیفون');
  String get clientTypes => _text('HTTP and SOCKS5', 'HTTP و SOCKS5');
  String get manualOnly => _text(
    'Use each port with the matching proxy type.',
    'هر پورت را با نوع پراکسی خودش وارد کن.',
  );
  String get host => _text('Server', 'Server');
  String get hostHint =>
      _text('IP address of this phone’s hotspot', 'IP هات‌اسپات این گوشی');
  String get port => _text('HTTP port', 'پورت HTTP');
  String get httpPort => _text('HTTP port', 'پورت HTTP');
  String get socks5Port => _text('SOCKS5 port', 'پورت SOCKS5');
  String get changeHttpPort => _text('Change HTTP port', 'تغییر پورت HTTP');
  String get changeSocksPort =>
      _text('Change SOCKS5 port', 'تغییر پورت SOCKS5');
  String get username => _text('Proxy username', 'نام کاربری پروکسی');
  String get password => _text('Proxy password', 'گذرواژهٔ پروکسی');
  String get copy => _text('Copy', 'کپی');
  String get copyHost => _text('Copy server', 'کپی Server');
  String get copyPort => _text('Copy port', 'کپی پورت');
  String get copyUsername => _text('Copy username', 'کپی نام کاربری');
  String get copyPassword => _text('Copy password', 'کپی گذرواژه');
  String get copyProfile =>
      _text('Copy HTTP and SOCKS5 setup', 'کپی تنظیم HTTP و SOCKS5');
  String get confirmCopyTitle =>
      _text('Copy proxy addresses?', 'آدرس‌های پراکسی کپی شوند؟');
  String get confirmCopyBody => _text(
    'This copies the Server, HTTP port and SOCKS5 port shown here.',
    'Server، پورت HTTP و پورت SOCKS5 همین صفحه کپی می‌شوند.',
  );
  String get cancel => _text('Cancel', 'لغو');
  String get confirmCopy => _text('Copy', 'کپی');
  String get copied => _text('Copied to clipboard', 'در کلیپ‌بورد کپی شد');
  String get profileCopied =>
      _text('Proxy addresses copied', 'آدرس‌های پراکسی کپی شدند');
  String get securityTitle => _text('Hotspot access', 'دسترسی هات‌اسپات');
  String get securityWarning => _text(
    'Neither port has a password. Any device connected to this hotspot can use both proxies. Keep the hotspot private and turn sharing off when you finish.',
    'هیچ‌کدام از دو پورت رمز ندارند و هر دستگاه متصل به هات‌اسپات می‌تواند از هر دو استفاده کند. هات‌اسپات را خصوصی نگه دار و بعد از کار اشتراک را خاموش کن.',
  );
  String get listenerOff => _text(
    'Hotspot proxies are off. Turn them on when the other device is connected to this phone’s hotspot.',
    'پراکسی‌های هات‌اسپات خاموش‌اند. وقتی دستگاه دیگر به هات‌اسپات وصل شد، آن‌ها را روشن کن.',
  );
  String get authMissing => _text(
    'The proxy is listening on the LAN without a login. Set a username and password in CLXY Settings before using it from another device.',
    'پروکسی در شبکهٔ محلی بدون ورود کاربری در دسترس است. پیش از استفاده از دستگاه دیگر، در تنظیمات CLXY نام کاربری و گذرواژه بگذارید.',
  );
  String get hostMissing => _text(
    'Turn on this phone’s hotspot and refresh the address list.',
    'هات‌اسپات این گوشی را روشن و فهرست IPها را تازه کن.',
  );
  String get openSettings =>
      _text('Open CLXY Settings', 'رفتن به تنظیمات CLXY');
  String get connectionSettings =>
      _text('Connection settings', 'تنظیمات اتصال');
  String get authBeforeLanTitle =>
      _text('Set a proxy login first', 'ابتدا ورود پروکسی را تنظیم کنید');
  String get authBeforeLanBody => _text(
    'Open Connection settings and save both a proxy username and password. Sharing stays off until both are set.',
    'به تنظیمات اتصال برو و نام کاربری و گذرواژهٔ پروکسی را ذخیره کن. تا وقتی هر دو را نگذاری، اشتراک خاموش می‌ماند.',
  );
  String get authMustRemainBody => _text(
    'LAN access is enabled. To remove either credential, turn off LAN access first. Both a username and password are required while the proxy listens on the LAN.',
    'دسترسی شبکهٔ محلی فعال است. برای حذف هرکدام از مشخصات ورود، ابتدا این دسترسی را خاموش کنید. تا وقتی پروکسی در شبکهٔ محلی شنود می‌کند، نام کاربری و گذرواژه هر دو لازم‌اند.',
  );
  String get ok => _text('OK', 'باشه');
  String get loadFailed => _text(
    'Could not read CLXY proxy settings. Try again.',
    'خواندن تنظیمات پروکسی CLXY ممکن نشد. دوباره تلاش کنید.',
  );
  String get retry => _text('Try again', 'تلاش دوباره');
  String get portSaved => _text('HTTP port saved', 'پورت HTTP ذخیره شد');
  String get socksPortSaved =>
      _text('SOCKS5 port saved', 'پورت SOCKS5 ذخیره شد');
  String get waitingForCLXY =>
      _text('Waiting for CLXY to connect', 'در انتظار اتصال CLXY');
  String get listening =>
      _text('HTTP and SOCKS5 ready', 'پراکسی‌های HTTP و SOCKS5 آماده‌اند');
  String get bindFailed => _text(
    'Could not start one of the hotspot proxy listeners.',
    'یکی از پراکسی‌های هات‌اسپات شروع نشد.',
  );
  String get addressChanged => _text(
    'The hotspot address changed. Apply the current address below.',
    'آدرس هات‌اسپات عوض شده. آدرس فعلی را از پایین اعمال کن.',
  );

  String _text(String english, String persian) => _persian ? persian : english;
}
