#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
  let constructor = tauri::Builder::default()
    // Guardar archivos en Android (MDL-184).
    //
    // Un WebView de Android no tiene gestor de descargas: el patrón de
    // navegador —URL `blob:` + enlace con `download`— no hace nada, sin error ni
    // aviso. Se comprobó dentro del APK el 18 de septiembre de 2026: el acta
    // firmada llegaba del backend con un 200 y se perdía.
    //
    // `dialog` abre el «Guardar como» del sistema (ACTION_CREATE_DOCUMENT) y
    // autoriza para escritura solo la ruta que el usuario elige; `fs` escribe
    // los bytes en ella. Son los plugins oficiales de Tauri, MIT/Apache, y se
    // prefirieron a un DownloadListener propio porque eso sería Kotlin a mano
    // dentro de `gen/android` que `tauri android init` borraría al regenerar.
    //
    // Se registran también en escritorio porque el `Builder` es uno solo, pero
    // allí no se usan: el WebView de escritorio sí descarga, y el permiso para
    // invocarlos está acotado a Android (capabilities/movil.json).
    .plugin(tauri_plugin_dialog::init())
    .plugin(tauri_plugin_fs::init());

  // Actualizarse sola, sin volver a instalar el paquete (ADR-017).
  //
  // Hasta aquí, cada versión obligaba a que alguien le llevara el instalador a
  // cada supervisor y a que cada supervisor lo ejecutara. Eso tiene un coste
  // que no se ve en una captura: el día que el servidor exige una pantalla que
  // solo trae la versión nueva —el paso 2.4 en la 0.6.0, «Cambiar contraseña»
  // en la 0.9.0— el supervisor que no actualizó no puede trabajar, y no tiene
  // forma de saber por qué. El actualizador cierra ese desfase solo.
  //
  // `updater` consulta el `latest.json` de la última versión publicada en
  // GitHub, comprueba su firma con la clave pública de tauri.conf.json e
  // instala; `process` es lo que permite reiniciar la aplicación al terminar.
  // Sin firma valida no instala nada: la clave privada vive en los secretos del
  // repositorio y nunca sale del flujo que publica.
  //
  // Solo en escritorio. El actualizador de Tauri no existe para Android —allí
  // la instalación la autoriza el sistema, no la aplicación— y por eso sus
  // dependencias de Rust están declaradas para `cfg(not(android, ios))` en
  // Cargo.toml: registrarlo en el APK no compilaría. Lo que sí hace el APK es
  // avisar de que hay versión nueva y llevar a descargarla
  // (services/autoactualizacion.ts).
  #[cfg(desktop)]
  let constructor = constructor
    .plugin(tauri_plugin_updater::Builder::new().build())
    .plugin(tauri_plugin_process::init());

  constructor
    .setup(|app| {
      if cfg!(debug_assertions) {
        app.handle().plugin(
          tauri_plugin_log::Builder::default()
            .level(log::LevelFilter::Info)
            .build(),
        )?;
      }
      Ok(())
    })
    .run(tauri::generate_context!())
    .expect("error while running tauri application");
}
