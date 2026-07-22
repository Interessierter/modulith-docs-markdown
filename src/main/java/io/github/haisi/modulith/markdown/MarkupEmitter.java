package io.github.haisi.modulith.markdown;

/**
 * The single seam between {@link ModulithDocusaurusRenderer} and the concrete flavour of Markdown it writes. MDX is a
 * strict superset of Markdown — Mermaid fences and {@code :::} admonitions work in both {@code .md} and {@code .mdx}
 * files — so there is exactly one renderer, not a parallel {@code Md}/{@code Mdx} class hierarchy. An MDX-flavoured
 * emitter only needs to override this seam (e.g. to emit a leading {@code import} for a React component); everything
 * else in the renderer stays the same.
 */
public interface MarkupEmitter {

	/**
	 * The file extension pages are written with, without the leading dot.
	 */
	String fileExtension();

	/**
	 * Renders a fenced Mermaid diagram block from a raw Mermaid definition.
	 */
	String mermaid(String definition);

	/**
	 * Content emitted once at the very top of a page, after the front matter. The default (plain Markdown) emitter
	 * has nothing to add here; an MDX emitter could use this to declare component imports.
	 */
	default String preamble() {
		return "";
	}

	/**
	 * The default emitter: plain, Docusaurus-flavoured {@code .md}. No MDX-only affordances are used.
	 */
	static MarkupEmitter markdown() {
		return PlainMarkdownEmitter.INSTANCE;
	}

	enum PlainMarkdownEmitter implements MarkupEmitter {

		INSTANCE;

		@Override
		public String fileExtension() {
			return "md";
		}

		@Override
		public String mermaid(String definition) {
			return "```mermaid" + System.lineSeparator()
					+ definition.stripTrailing() + System.lineSeparator()
					+ "```" + System.lineSeparator();
		}
	}
}
