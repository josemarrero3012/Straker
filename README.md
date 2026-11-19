# Straker para Android

Aplicación en español para llevar un registro manual de apuestas y cuentas en euros. Funciona desde Android 11, con los datos y la interfaz guardados en el dispositivo.

## Primeros pasos

1. Abre **Bancas** y pulsa **Nueva cuenta**. Indica la casa de apuestas, un nombre para la banca y su saldo inicial. También puedes crear cuentas bancarias para seguir las transferencias.
2. En **Nueva apuesta**, introduce el evento, el importe, la cuota y la banca. El saldo disponible baja por el importe jugado.
3. Toca la apuesta, elige un estado y guarda. Una ganada devuelve el importe multiplicado por la cuota, una nula devuelve el importe original y una perdida devuelve cero. En un cash out se abona el retorno total que indiques. Puedes corregir el estado después.
4. En **Movimientos**, registra ingresos y retiradas desde o hacia el exterior. Para traspasos y transferencias selecciona dos cuentas. El total entre ambas se conserva.

El resultado neto suma los retornos de las apuestas liquidadas y resta sus importes jugados. El dinero pendiente aparece en **En juego**. Desinstalar la aplicación o borrar sus datos elimina el registro local.

## Compilar

Abre esta carpeta en Android Studio con JDK 17 y Android SDK 35 instalados. También puedes compilar en Windows con `gradlew.bat assembleDebug`. El APK de desarrollo se genera en `app/build/outputs/apk/debug/`.

La actividad Android presenta una interfaz WebView que carga los recursos incluidos en el APK. La lógica contable está en `app/src/main/assets/ledger.js`. Las tipografías Barlow y Barlow Condensed se distribuyen con sus licencias en la carpeta `fonts`.

