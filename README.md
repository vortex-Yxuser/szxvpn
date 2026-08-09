# SSH Payload VPN — clean starter

هذا مشروع Android جديد من الصفر، هدفه أن يكون بسيطًا وسهل التشخيص:

- واجهة إعداد SSH.
- HTTP proxy.
- Payload placeholders:
  - `[host]`
  - `[port]`
  - `[host_port]`
  - `[protocol]`
  - `[crlf]`
  - `[lf]`
- سجل اتصال واضح داخل التطبيق.
- أخطاء HTTP CONNECT تظهر في السجل.
- اتصال SSH عبر JSch.
- طلب صلاحية Android VPN.
- إنشاء واجهة TUN بعد نجاح SSH.

## مهم جدًا

هذه النسخة **ليست بعد عميل VPN كامل لتوجيه كل حزم الهاتف عبر SSH**.
هي طبقة نظيفة قابلة للبناء والاختبار، وتنفذ:

`Android -> HTTP Proxy -> Payload -> SSH`

ثم تنشئ واجهة `VpnService`.

أما تمرير حزم TUN إلى SSH ثم إرجاع الردود (TUN <-> TCP/SSH forwarding)
فيحتاج محرك packet forwarding / tun2socks أو تنفيذ native مناسب. لم أضع
محركًا غير موثوق في المشروع حتى لا نحصل على APK "متصل" شكليًا بينما الإنترنت
لا يعمل.

## Build

```bash
./gradlew clean
./gradlew assembleDebug
```

في Codemagic يمكن استخدام:

```bash
./gradlew :app:assembleDebug --stacktrace
```

## Payload example

```text
CONNECT [host_port] [protocol][crlf]Host: api.Snapchat.com[crlf][crlf]
```

## اختبار المشكلة القديمة

إذا كان البروكسي لا يعيد:

```text
HTTP/1.1 200
```

فالسجل سيظهر السبب بدل `Read timed out` فقط.

بعد نجاح SSH، المرحلة التالية هي إضافة محرك TUN-to-SSH فعلي.
