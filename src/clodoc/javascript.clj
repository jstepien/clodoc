(ns clodoc.javascript
  [:import [com.google.javascript.jscomp CommandLineRunner CompilerOptions
                                         JSSourceFile]])

(defmacro compress
  [& lines]
  `~(let [compiler (com.google.javascript.jscomp.Compiler.)
          options (CompilerOptions.)
          input [(JSSourceFile/fromCode "input.js" (apply str lines))]
          externs (CommandLineRunner/getDefaultExterns)]
      (.compile compiler externs input options)
      (.toSource compiler)))
