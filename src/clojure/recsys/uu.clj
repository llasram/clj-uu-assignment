(ns recsys.uu
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [parenskit (vector :as lkv) (core :as lkc)]
            [esfj.provider :refer [defprovider]])
  (:import [java.util Collection Map Set]
           [com.google.common.collect Maps Sets]
           [org.grouplens.lenskit ItemScorer]
           [org.grouplens.lenskit.data.dao EventDAO ItemDAO UserDAO]
           [org.grouplens.lenskit.data.dao UserEventDAO ItemEventDAO]
           [org.grouplens.lenskit.data.event Rating]
           [org.grouplens.lenskit.data.history
             History RatingVectorUserHistorySummarizer UserHistory]
           [org.grouplens.lenskit.vectors SparseVector]
           [org.grouplens.lenskit.vectors MutableSparseVector]
           [org.grouplens.lenskit.vectors.similarity CosineVectorSimilarity]
           [recsys.dao ItemTitleDAO MOOCRatingDAO MOOCItemDAO MOOCUserDAO]
           [recsys.dao RatingFile TitleFile UserFile]))

(def ^:const knn-k
  "The number of nearest neighbors to use in user-user scoring."
  30)

(defn get-uvec
  "Retrieve rating vector of all items for `user` from `udao`."
  [^UserEventDAO udao ^long user]
  (->> (or (.getEventsForUser udao user Rating) (History/forUser user))
       (RatingVectorUserHistorySummarizer/makeRatingVector)))

(defn mean-center
  "Mean-center vector `v`."
  ^SparseVector [^SparseVector v]
  (-> v lkv/mvec (lkv/-! (.mean v)) lkv/freeze!))

(defprovider suu-item-scorer
  "Simple user-user item score implementation."
  ^ItemScorer [^UserEventDAO udao ^ItemEventDAO idao]
  (lkc/item-scorer
   (fn score [^long user ^MutableSparseVector scores]
     (let [uvec (get-uvec udao user), umean (.mean ^SparseVector uvec),
           uvec (mean-center uvec), vs (CosineVectorSimilarity.)]
       (lkv/map-keys!
        (fn [^long item]
          (->> (.getUsersForItem idao item)
               (remove (partial = user))
               (map (fn [^long user']
                      (let [uvec' (mean-center (get-uvec udao user'))
                            sim (.similarity vs uvec uvec')]
                        [sim uvec'])))
               (sort-by first >)
               (take knn-k)
               (reduce (fn [[s w] [^double sim ^SparseVector uvec']]
                         [(+ s (* sim (.get uvec' item)))
                          (+ w (Math/abs sim))])
                       [0.0 0.0])
               (apply /)
               (+ umean)))
        :either scores)))))

(defn configure-recommender
  "Create the LensKit recommender configuration."
  []
  (doto (lkc/config)
    (-> (.bind EventDAO) (.to MOOCRatingDAO))
    (-> (.set RatingFile) (.to (io/file "data/ratings.csv")))
    (-> (.bind ItemDAO) (.to MOOCItemDAO))
    (-> (.set TitleFile) (.to (io/file "data/movie-titles.csv")))
    (-> (.bind UserDAO) (.to MOOCUserDAO))
    (-> (.set UserFile) (.to (io/file "data/users.csv")))
    (-> (.bind ItemScorer) (.toProvider suu-item-scorer))))

(defn parse-args
  "Parse the command-line arguments in sequence `args`.  Use Java collections
to keep output order consistent with Java implementation."
  [args]
  (reduce (fn [^Map to-score arg]
            (let [[uid iid] (map #(Long/parseLong %) (str/split arg #":"))]
              (when-not (.containsKey to-score uid)
                (.put to-score uid (Sets/newHashSet)))
              (-> to-score ^Set (.get uid) (.add iid))
              to-score))
          (Maps/newHashMap) args))

(defn -main
  "Main entry point to the program."
  [& args]
  (let [to-score (parse-args args)
        config (configure-recommender)
        rec (lkc/rec-build config)
        scorer (.getItemScorer rec)
        tdao (lkc/rec-get rec ItemTitleDAO)]
    (doseq [[^Long user ^Collection items] to-score
            :let [_ (log/info "scoring" (count items) "items for user" user)
                  scores (.score scorer user items)]
            ^Long item items
            :let [score (if-not (.containsKey scores item)
                          "NA"
                          (format "%.4f" (.get scores item)))
                  title (.getItemTitle tdao item)]]
      (printf "%d,%d,%s,%s\n" user item score title))))
