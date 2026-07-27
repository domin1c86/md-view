# commonmark discovers its extension classes reflectively in places; keep the
# public AST node types so the parser and our visitor stay in agreement.
-keep class org.commonmark.node.** { *; }

# The extensions register parsers and custom node types (tables, strikethrough,
# front matter) the same way. The renderer matches on those classes by identity,
# so they have to survive shrinking too.
-keep class org.commonmark.ext.** { *; }
