(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'stock-dash/stock-dash)
(def version "0.1.0")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

;; 編譯 Java bindings
(defn compile-java [_]
  (println "Compiling Java bindings...")
  (b/javac {:src-dirs ["native/generated"]
            :class-dir class-dir
            :basis basis
            :javac-opts ["--release" "25"]})
  (println "✓ Java bindings compiled"))

(defn uber [_]
  (clean nil)
  (compile-java nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  ;; 複製 native libraries 到 uberjar
  (b/copy-dir {:src-dirs ["native/lib"]
               :target-dir (str class-dir "/native/lib")})
  (b/compile-clj {:basis basis
                  :ns-compile '[stock-dash.core]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'stock-dash.core})
  (println "Uberjar created:" uber-file))
