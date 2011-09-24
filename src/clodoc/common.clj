(ns clodoc.common)

(defmacro
  ^{:private 1}
  stdout-of
  [#^String cmd]
  `~(.trim ^String (slurp (.getInputStream (.exec (Runtime/getRuntime) cmd)))))

(def version
  (apply str (take 8 (stdout-of "git log --format=oneline HEAD~.."))))
