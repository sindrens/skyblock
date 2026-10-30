package net.skybornmc.skyblock.worlds.builds;

/**
 * One problem of a build. Errors stop {@code paste} and {@code apply}; warnings are shown but don't.
 *
 * @param text staff-facing description (shown in {@code /skyblock builds info} and the console log)
 */
public record BuildProblem(boolean error, String text) {

    public static BuildProblem error(String text) {
        return new BuildProblem(true, text);
    }

    public static BuildProblem warning(String text) {
        return new BuildProblem(false, text);
    }
}
