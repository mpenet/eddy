(ns build
  (:refer-clojure :exclude [compile])
  (:require [clojure.tools.build.api :as b]))

(set! *warn-on-reflection* true)

(def lib 'com.s-exp/lido)
(def version (format "1.0.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))
(def copy-srcs ["src" "resources"])

(defn prep [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir}))

(defn clean
  [_]
  (b/delete {:path "target"}))

(defn jar
  [{:keys [env] :as params}]
  (let [srcs (if (= env :dev) (cons "dev-resources" copy-srcs) copy-srcs)]
    (b/write-pom {:class-dir class-dir
                  :lib lib
                  :version version
                  :basis basis
                  :src-dirs ["src"]})
    (b/copy-dir {:src-dirs srcs
                 :target-dir class-dir})
    (b/jar {:class-dir class-dir
            :jar-file jar-file})
    params))

(defn uber
  [_]
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis}))

(defn compile
  [_]
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir}))

(defn all
  [_]
  (clean nil)
  (prep nil)
  (compile nil)
  (uber nil))

;; clj -T:build clean
;; clj -T:build prep
;; clj -T:build uber
