# Mail libraries

Android 5.1 compatible JavaMail / Activation artifacts, downloaded from Maven Central:

| Artifact | Version | SHA-256 |
| --- | --- | --- |
| com.sun.mail:android-mail | 1.6.7 | 12c4240aa3a4ccda37c49b4e6b880a797a21e03a4f8c7421a1b51e0db552b79c |
| com.sun.mail:android-activation | 1.6.7 | 285713b2b549f382f26a57919ecddc4c83d9755737969fb7c006c905322bb253 |

Sources: [JavaMail for Android](https://javaee.github.io/javamail/Android), [android-mail](https://repo.maven.apache.org/maven2/com/sun/mail/android-mail/1.6.7/), [android-activation](https://repo.maven.apache.org/maven2/com/sun/mail/android-activation/1.6.7/).

Original LICENSE and NOTICE files are adjacent to the JARs and bundled in `assets/licenses` in the APK. Flip Post selects IMAPSSLStore explicitly and registers the required MIME handlers in code; it does not package unsigned META-INF configuration entries.
