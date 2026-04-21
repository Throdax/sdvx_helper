package com.sdvxhelper.app;

/**
 * Fat-JAR entry point for the Updater application.
 *
 * <p>
 * When a Shade uber-JAR's {@code Main-Class} manifest attribute points directly
 * to a class that extends {@link javafx.application.Application}, the JVM
 * performs a strict module-system check at startup (before {@code main()} is
 * ever called) and throws <em>"Error: JavaFX runtime components are
 * missing"</em> because JavaFX is not on the module path in a classpath-only
 * fat JAR.
 * </p>
 *
 * <p>
 * This class breaks the cycle: it is an ordinary class with no JavaFX
 * supertype, so the module check is skipped. It simply delegates to
 * {@link UpdaterApp#main(String[])}, which calls
 * {@link javafx.application.Application#launch(Class, String...)} internally —
 * a code path that is tolerant of the classpath deployment model.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class Launcher {

    /**
     * Application entry point; delegates immediately to the real JavaFX
     * application.
     *
     * @param args
     *            command-line arguments forwarded to JavaFX
     */
    public static void main(String[] args) {
        System.setProperty("app.log.name", "update");
        UpdaterApp.main(args);
    }
}
