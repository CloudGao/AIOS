package os.aios.codex;

/** Current locale policy; call at every thread creation/resume, never cache. */
final class CommunicationLanguage {
    static String instructions(java.util.Locale locale) {
        return PresentationPolicy.INSTRUCTIONS + "\n[Current communication language] The phone's current primary system locale is "
            +locale.toLanguageTag()+" ("+locale.getDisplayName(java.util.Locale.ENGLISH)+"). "
            +"Use this language and writing system by default for all user-facing replies, progress, questions, confirmation titles/options, and generated task titles. "
            +"An explicit user request for another language or translation takes precedence. The language of old conversation turns, internal instructions, tools, or quoted material does not override this current default. "
            +"Preserve names, code, paths, identifiers, and quoted source text. Do not translate machine-readable tool arguments or protocol enums. "
            +"The latest application context named aios-system-language is the authoritative current default and supersedes the initial locale and older language context, including in an existing conversation.";
    }
}
