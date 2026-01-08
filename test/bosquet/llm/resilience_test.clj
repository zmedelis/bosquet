(ns bosquet.llm.resilience-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [bosquet.llm.resilience :as resilience]
   [bosquet.llm.wkk :as wkk]))

(use-fixtures :each (fn [f] (resilience/reset-circuit-breakers!) (f)))

(deftest retry-test
  (testing "retries on retryable error"
    (let [call-count (atom 0)]
      (is (= :success
             (resilience/with-retry
               (fn []
                 (swap! call-count inc)
                 (if (< @call-count 3)
                   (throw (ex-info "Rate limited" {:retryable? true}))
                   :success))
               {:max-attempts 3 :backoff-ms 10})))
      (is (= 3 @call-count))))

  (testing "fails immediately on non-retryable error"
    (let [call-count (atom 0)]
      (is (thrown? Exception
                   (resilience/with-retry
                     (fn []
                       (swap! call-count inc)
                       (throw (ex-info "Auth error" {:retryable? false})))
                     {:max-attempts 3})))
      (is (= 1 @call-count))))

  (testing "exhausts max attempts on persistent retryable error"
    (let [call-count (atom 0)]
      (is (thrown? Exception
                   (resilience/with-retry
                     (fn []
                       (swap! call-count inc)
                       (throw (ex-info "Rate limited" {:retryable? true})))
                     {:max-attempts 3 :backoff-ms 10})))
      (is (= 3 @call-count)))))

(deftest fallback-test
  (testing "falls back to secondary provider"
    (let [calls (atom [])]
      (is (= {:result "success from claude"}
             (resilience/with-fallback
               (fn [_ provider _]
                 (swap! calls conj (wkk/service provider))
                 (if (= :openai (wkk/service provider))
                   (throw (ex-info "Service down" {:recoverable? true}))
                   {:result "success from claude"}))
               {:primary {wkk/service :openai}
                :fallbacks [{wkk/service :claude}]
                :retry {:max-attempts 1}
                :circuit-breaker {}}
               {}
               [])))
      (is (= [:openai :claude] @calls))))

  (testing "does not fall back on non-recoverable error"
    (let [calls (atom [])]
      (is (thrown? Exception
                   (resilience/with-fallback
                     (fn [_ provider _]
                       (swap! calls conj (wkk/service provider))
                       (throw (ex-info "Bad request" {:recoverable? false})))
                     {:primary {wkk/service :openai}
                      :fallbacks [{wkk/service :claude}]
                      :retry {:max-attempts 1}
                      :circuit-breaker {}}
                     {}
                     [])))
      (is (= [:openai] @calls))))

  (testing "succeeds with primary provider"
    (let [calls (atom [])]
      (is (= {:result "success from openai"}
             (resilience/with-fallback
               (fn [_ provider _]
                 (swap! calls conj (wkk/service provider))
                 {:result "success from openai"})
               {:primary {wkk/service :openai}
                :fallbacks [{wkk/service :claude}]
                :retry {:max-attempts 1}
                :circuit-breaker {}}
               {}
               [])))
      (is (= [:openai] @calls))))

  (testing "throws when all providers fail"
    (let [calls (atom [])]
      (is (thrown-with-msg? Exception #"All providers failed"
                            (resilience/with-fallback
                              (fn [_ provider _]
                                (swap! calls conj (wkk/service provider))
                                (throw (ex-info "Service down" {:recoverable? true})))
                              {:primary {wkk/service :openai}
                               :fallbacks [{wkk/service :claude}
                                           {wkk/service :mistral}]
                               :retry {:max-attempts 1}
                               :circuit-breaker {}}
                              {}
                              [])))
      (is (= [:openai :claude :mistral] @calls)))))

(deftest complete-fallback-test
  (testing "completion falls back to secondary provider"
    (let [calls (atom [])]
      (is (= {:result "completion from mistral"}
             (resilience/with-fallback
               (fn [_ provider prompt]
                 (swap! calls conj (wkk/service provider))
                 (if (= :openai (wkk/service provider))
                   (throw (ex-info "Service down" {:recoverable? true}))
                   {:result "completion from mistral"}))
               {:primary {wkk/service :openai}
                :fallbacks [{wkk/service :mistral}]
                :retry {:max-attempts 1}
                :circuit-breaker {}}
               {}
               "Complete this: Hello")))
      (is (= [:openai :mistral] @calls))))

  (testing "completion succeeds with primary provider"
    (let [calls (atom [])]
      (is (= {:result "completion from openai"}
             (resilience/with-fallback
               (fn [_ provider prompt]
                 (swap! calls conj (wkk/service provider))
                 {:result "completion from openai"})
               {:primary {wkk/service :openai}
                :fallbacks [{wkk/service :mistral}]
                :retry {:max-attempts 1}
                :circuit-breaker {}}
               {}
               "Complete this: Hello")))
      (is (= [:openai] @calls)))))
