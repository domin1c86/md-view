# commonmark discovers its extension classes reflectively in places; keep the
# public AST node types so the parser and our visitor stay in agreement.
-keep class org.commonmark.node.** { *; }
