# Straker 1.0 para Android

Straker registra apuestas manuales y movimientos en euros. El panel utiliza Jetpack Compose y Material 3 con las ilustraciones originales de la cancha y las entradas. Requiere Android 11 o posterior.

## Abrir el proyecto

Abre la carpeta que contiene `settings.gradle` en Android Studio. Utiliza JDK 17 y Android SDK 35. En Windows, `gradlew.bat assembleDebug` genera el APK de desarrollo y `gradlew.bat testReleaseUnitTest` ejecuta la prueba de Room.

## Sesión y registro

La pantalla de bienvenida ofrece inicio de sesión, registro con Google y modo local. El registro crea un perfil de Straker en este dispositivo para una cuenta Google existente. Credential Manager obtiene un token con un nonce aleatorio. Antes de activar la sesión, la aplicación verifica la firma con las claves públicas de Google, el emisor, el destinatario, el nonce y la caducidad. Conserva el identificador de perfil para abrir su base local al reiniciar. Salir cierra la sesión. La autenticación requiere conexión y servicios de Google Play.

Para habilitar el botón de Google en una compilación:

1. Configura Google Auth Platform en tu proyecto de Google Cloud y completa la pantalla de consentimiento. Si el proyecto está en pruebas, añade las cuentas de prueba que vayan a utilizarlo.
2. Registra un cliente OAuth de tipo Android con el paquete `es.straker.app` y la huella SHA-1 del certificado que firma el APK. El APK entregado utiliza `FE:3D:7E:E8:B1:70:79:3A:B0:17:7C:69:0A:0F:E2:3B:C1:1F:25:92`. Una compilación de depuración o una distribución con Play App Signing utiliza su certificado correspondiente.
3. En el mismo proyecto crea un cliente OAuth de tipo **Aplicación web**. Usa su ID público como propiedad Gradle `STRAKER_GOOGLE_WEB_CLIENT_ID`. Por ejemplo, añade `STRAKER_GOOGLE_WEB_CLIENT_ID=TU_ID.apps.googleusercontent.com` a las propiedades de Gradle del usuario.
4. Recompila e instala. Entra en **Crear un perfil → Registrarme con Google**, elige una cuenta, cierra la sesión y prueba **Continuar con Google**. El modo local está disponible desde la primera instalación.

[Guía oficial de Google para Android](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation)

## Arquitectura y datos

- La interfaz nativa Compose observa los flujos de los ViewModels con el ciclo de vida de la pantalla. Navigation Compose organiza el acceso, el registro, las cuatro secciones principales y sus formularios y detalles.
- `SessionViewModel` gestiona la identidad local activa. `LedgerViewModel` expone el estado de la contabilidad y delega las escrituras a `LedgerRepository`.
- Room almacena cuentas, apuestas y movimientos en tablas propias. Cada perfil tiene un archivo de base de datos independiente. El registro personal y sus operaciones permanecen en este dispositivo.
- Las operaciones validan origen, destino, saldo, cuota y retorno en una transacción. Los importes se contabilizan en céntimos. Los cambios de estado recalculan el retorno para evitar duplicar abonos.

