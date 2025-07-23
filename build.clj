(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]
            [borkdude.gh-release-artifact :as gh]))

(def lib 'com.eldrix/nhspd)
(def version (format "2.0.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-basis (b/create-basis {:project "deps.edn" :aliases [:run]}))
(def jar-file (format "target/%s-lib-%s.jar" (name lib) version))
(def uber-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar
  "Create a library jar file."
  [_]
  (clean nil)
  (println "Building" jar-file)
  (b/write-pom {:class-dir class-dir
                :lib       lib
                :version   version
                :basis     basis
                :src-dirs  ["src"]
                :scm       {:url                 "https://github.com/wardle/nhspd"
                            :tag                 (str "v" version)
                            :connection          "scm:git:git://github.com/wardle/nhspd.git"
                            :developerConnection "scm:git:ssh://git@github.com/wardle/nhspd.git"}
                :pom-data  [[:description
                             "Support for the UK NHS Postcode directory, linking all UK postcodes
                             to administrative and political areas, including LSOA for small
                             region population (1000-1500 people) analytics."]
                            [:developers
                             [:developer
                              [:id "wardle"] [:name "Mark Wardle"] [:email "mark@wardle.org"] [:url "https://wardle.org"]]]
                            [:organization [:name "Eldrix Ltd"]]
                            [:licenses
                             [:license
                              [:name "Eclipse Public License v2.0"]
                              [:url "https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html"]
                              [:distribution "repo"]]]]})
  (b/copy-dir {:src-dirs   ["src" "resources"]
               :target-dir class-dir})

  (b/jar {:class-dir class-dir
          :jar-file  jar-file}))

(defn install
  "Install library to local maven repository."
  [_]
  (clean nil)
  (jar nil)
  (b/install {:basis     basis
              :lib       lib
              :version   version
              :jar-file  jar-file
              :class-dir class-dir}))

(defn deploy
  "Deploy library to clojars.
  Environment variables CLOJARS_USERNAME and CLOJARS_PASSWORD must be set."
  [_]
  (clean nil)
  (jar nil)
  (dd/deploy {:installer :remote
              :artifact  jar-file
              :pom-file  (b/pom-path {:lib       lib
                                      :class-dir class-dir})}))

(defn uber
  "Create an executable uberjar file."
  [_]
  (clean nil)
  (println "Building uberjar" uber-file)
  (b/copy-dir {:src-dirs   ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis     uber-basis
                  :src-dirs  ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     uber-basis
           :main      'com.eldrix.nhspd.cmd}))

(defn release
  "Deploy release to GitHub. Requires valid token in GITHUB_TOKEN environmental
   variable."
  [_]
  (uber nil)
  (println "Deploying release to GitHub")
  (gh/release-artifact {:org    "wardle"
                        :repo   "nhspd"
                        :tag    (str "v" version)
                        :file   uber-file
                        :sha256 true}))
