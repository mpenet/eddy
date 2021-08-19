(ns build
  (:refer-clojure :exclude [compile])
  (:require [clojure.tools.build.api :as b]))

(set! *warn-on-reflection* true)

(def lib 'com.s-exp/eddy)
(def version (format "1.0.0-alpha%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))
(def copy-srcs ["src" "resources"])

(defn prep [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                ;; :scm {:tag (str "v" version)}
                :basis basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir}))

(defn clean
  [_]
  (b/delete {:path "target"}))

(defn clean "Remove the target folder." [_]
  (println "\nCleaning target...")
  (b/delete {:path "target"}))

(defn jar "Build the library JAR file." [_]
  (println "\nWriting pom.xml...")
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :scm {:tag (str "v" version)}
                :basis basis
                :src-dirs ["src"]})
  (println "Copying src...")
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (println (str "Building jar " jar-file "..."))
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn compile
  [_]
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir}))

;; clj -T:build clean
;; clj -T:build prep
;; clj -T:build uber
