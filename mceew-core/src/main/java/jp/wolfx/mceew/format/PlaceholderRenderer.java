package jp.wolfx.mceew.format;

/**
 * Applies placeholder/replacement pairs with the legacy replaceAll semantics.
 */
public final class PlaceholderRenderer {
    private PlaceholderRenderer() {
    }

    public static String render(String template, String... replacements) {
        String rendered = template;
        for (int index = 0; index < replacements.length; index += 2) {
            rendered = rendered.replaceAll(replacements[index], replacements[index + 1]);
        }
        return rendered;
    }
}
