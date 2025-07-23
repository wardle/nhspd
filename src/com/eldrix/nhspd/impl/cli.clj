(ns com.eldrix.nhspd.impl.cli
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [com.eldrix.nhspd.impl.model :as model])
  (:import (java.time LocalDate)
           (java.time.format DateTimeFormatter DateTimeParseException)))

(defn usage
  [{:keys [cmd help]} options-summary]
  (->>
    (if cmd
      ["nhspd - the UK NHS Postcode Directory"
       ""
       (str "Usage: clj -M:run [options] " (or help cmd))
       (str "or:    java -jar nhspd.jar [options] " (or help cmd))
       ""
       (str "Options for '" cmd "':")
       options-summary
       ""]
      ["nhspd - the UK NHS Postcode Directory"
       ""
       (str "Usage: clj -M:run [options] " (or help cmd))
       (str "or:    java -jar nhspd.jar [options] " (or help cmd))
       ""
       "All available options:"
       options-summary
       ""
       "Commands:"
       "  create   Create an NHSPD index file from latest release"
       "  update   Update an NHSPD index file from latest release"
       "  import   Manually import NHSPD files into an index"
       "  serve    Run a HTTP server"
       "  status   Get information about an NHSPD index file"
       ""])
    (str/join \newline)))

(defn parse-localdate
  [s]
  (when-not (str/blank? s)
    (try (LocalDate/parse s DateTimeFormatter/ISO_LOCAL_DATE)
         (catch DateTimeParseException _ nil))))

(def all-options
  {:db             [nil "--db DATABASE" "Path to index file"]
   :help           ["-h" "--help"]
   :profile        [nil "--profile PROFILE" "Profile (one of 'core','active','current' or 'all')"
                    :parse-fn keyword
                    :validate [(set (keys model/profiles)) "Invalid profile"]]
   :column         ["-c" "--column COLUMN_NAME" "Column(s) to import (e.g. -c PCT -c ICB -c LSOA11,LSOA21')"
                    :multi true
                    :parse-fn #(map (comp keyword str/upper-case) (str/split % #","))
                    :update-fn (fn [acc cols] (into (or acc []) cols))
                    :validate [(fn [cols] (s/valid? ::model/cols cols)) "Invalid column selection"]]
   :bind           ["-a" "--bind BIND_ADDRESS" "Address to bind"]
   :allowed-origin [nil "--allowed-origin \"*\" or ORIGIN" "Set CORS policy, with \"*\" or hostname"
                    :multi true :default [] :update-fn conj]
   :port           ["-p" "--port PORT" "Port number"
                    :default 8080
                    :parse-fn parse-long
                    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   :release        [nil "--release RELEASE_DATE" "Label for release for manual import, format yyyy-MM-dd e.g. 2025-03-01"
                    :parse-fn parse-localdate
                    :validate [some? "Must be a date of format yyyy-MM-dd e.g. 2025-03-01"]]
   :format         [nil "--format FORMAT" "Output format"
                    :parse-fn keyword
                    :validate [#{:json :edn :text} "Format must be 'json', 'edn' or 'text'"]]
   :delay          [nil "--delay DELAY" "Delay in milliseconds between batches during import for throttling"
                    :parse-fn parse-long
                    :validate [nat-int? "Must be a number of milliseconds"]]})

(def commands
  [{:cmd "create" :options [:db :help :profile :column]}
   {:cmd "update" :options [:db :help :profile :column :delay]}
   {:cmd "import" :options [:db :help :profile :column :release :delay] :help "import [file1] [file2] ..."}
   {:cmd "serve" :options [:db :help :port :bind :allowed-origin]}
   {:cmd "status" :options [:db :format :help]}])

(def all-commands (into #{} (map :cmd) commands))

(def command-by-name
  "Map of command to command configuration."
  (reduce (fn [acc {:keys [cmd options] :as conf}]
            (assoc acc cmd (assoc conf :options (mapv all-options options))))
          {} commands))

(defn with-usage [{:keys [summary] :as parsed} cmd-conf]
  (assoc parsed :usage (usage cmd-conf summary)))

(defn parse-args
  [args]
  (let [cmds (filter all-commands args)
        cmd (first cmds)
        args# (remove #(= cmd %) args)
        cmd-conf (command-by-name cmd)
        option-specs (if cmd-conf (:options cmd-conf) (vals all-options))
        {:keys [options] :as parsed}
        (-> (cli/parse-opts args# option-specs) (with-usage cmd-conf))]
    (cond
      ;; not asking for help and no command? => add custom error
      (and (not (:help options)) (nil? cmd-conf))
      (assoc parsed :errors ["Invalid or missing command"])
      ;; both profile and cols specified? => add custom error
      (and (:profile options) (:column options))
      (assoc parsed :errors ["Specify either --profile or --cols, not both"])
      ;; add identified command to parsed options / arguments / usage
      :else
      (assoc parsed :cmd cmd))))

(comment
  (parse-args [])
  (parse-args ["serve"])
  (parse-args ["serve" "--help"])
  (parse-args ["status" "--help"])
  (parse-args ["serve" "--port" "8080" "--bind" "0.0.0.0"])
  (parse-args ["serve" "--port" "8080" "--bind" "0.0.0.0" "--allowed-origin" "*"])
  (parse-args ["serve" "--port" "8080" "--bind" "0.0.0.0" "--release" "2022-01-01"])
  (parse-args ["import" "--db" "nhspd.db" "--release" "2022-01-01"])
  (parse-args ["import" "--db" "nhspd.db" "--release" "20220101"])
  (parse-args ["create"]))

