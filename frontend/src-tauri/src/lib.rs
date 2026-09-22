#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
  tauri::Builder::default()
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
    .plugin(tauri_plugin_fs::init())
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
