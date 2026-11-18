package stilllife.ftfbridge;

import java.nio.file.Path;
import java.util.Optional;

/** What the bridge needs from the mod loader. Each loader's entry point passes one to {@link StillLifeFtfBridge#init}. */
public interface Platform {
   /** The loader's name, for the log ("NeoForge", "Fabric"). */
   String name();

   /** The game's config folder. */
   Path configDir();

   /** The version of an installed mod, if it is installed. */
   Optional<String> modVersion(String modId);
}
