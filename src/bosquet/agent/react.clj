(ns bosquet.agent.react
  (:require
    [bosquet.agent.tool :as t]
    [bosquet.agent.agent-mind-reader :as mind-reader]
    [bosquet.generator :as generator]
    [bosquet.template.read :as template]
    [clojure.string :as string]
    [taoensso.timbre :as timbre]
    [taoensso.timbre.appenders.core :as appenders]))

(timbre/merge-config!
  {:appenders {:println {:enabled? false}
               :spit    (appenders/spit-appender {:fname "bosquet.log"})}})

(def prompt-palette (template/load-palettes "resources/prompt-palette/agent"))

(defn generate-thoughts
  "Generate ReAct thoughts.
  `ctx` has needed data points
  `prompt-palette` has all the prompts needed for ReAct
  `prompt-key` is the key to the prompt to be used to start the generation."
  [ctx prompt-palette prompt-key]
  (let [{gen-output  :thoughts
         full-output prompt-key}
        (generator/complete prompt-palette ctx [prompt-key :thoughts])
        prompt                   (string/replace full-output gen-output "")]
    ;; :resoning-trace will contain only the thoughts from before,
    ;; most recent observation goes into :thoughts
    {:reasoning-trace prompt
     :thoughts        gen-output}))

(defn focus-on-observation
  "Get the sentence a the position `lookup-index` from the observation."
  [{:keys [lookup-db lookup-index]}]
  ;; last is the position of the sentence in the tuple
  (last (get lookup-db lookup-index)))

(defn solve-task
  "Solve a task using [ReAct](https://react-lm.github.io)

  First `agent` parameter specifies which tool will be used to solve the task.
  Second context parameter gives initialization data to start working
  - `task` is a quesiton ar task formulation for the agent
  - `max-steps` specifies how many thinking steps agent is allowed to do
  it either reaches that number of steps or 'Finish' action, and then terminates.

  :react/task contains a question or a claim to be solved"
  [tool {:keys [task max-steps]
         :or   {max-steps 5}
         :as   initial-ctx}]
  (t/print-thought (format "'%s' tool has the following task" (t/my-name tool)) task)
  (let [{:keys [thoughts reasoning-trace]}
        (generate-thoughts initial-ctx prompt-palette :react/step-0)]
    (loop [step            1
           ctx             initial-ctx
           thoughts        thoughts
           reasoning-trace reasoning-trace]
      (let [{:keys [action thought parameters] :as action-ctx}
            (mind-reader/find-action step thoughts)
            ctx         (merge ctx action-ctx {:step step})
            _           (t/print-indexed-step "Thought" thought step)
            _           (t/print-action action parameters step)
            observation (t/call-tool tool action ctx)]
        (cond
          ;; Tool failed to find a solution in max steps allocated
          (= step max-steps)
          (do
              (t/print-too-much-thinking-error step)
              nil)

          ;; Tool got to the solution. Print and return it
          (= :finish action)
          (do
              (t/print-result observation)
              observation)

          ;; Continue thinking
          :else
          (let [current-observation (focus-on-observation observation)
                _                   (t/print-indexed-step "Observation" current-observation step)
                {:keys [thoughts reasoning-trace]}
                (generate-thoughts
                  {:step            (inc step)
                   :reasoning-trace (str reasoning-trace thought)
                   :observation     current-observation}
                  prompt-palette :react/step-n)]
            (recur (inc step) ctx thoughts reasoning-trace)))))))

(comment
  (def trace "Question: What is the elevation range for the area that the eastern sector of the\nColorado orogeny extends into?\nThought 1: I need to search Colorado orogeny, find the area that the eastern sector\nof the Colorado orogeny extends into, then find the elevation range of the area.\nAction 1: Search[Colorado orogeny]\nObservation 1: The Colorado orogeny was an episode of mountain building (an orogeny) in\nColorado and surrounding areas.\nThought 2: It does not mention the eastern sector. So I need to look up eastern sector.\nAction 2: Lookup[eastern sector]\nObservation 2: (Result 1 / 1) The eastern sector extends into the High Plains and is called\nthe Central Plains orogeny.\nThought 3: The eastern sector of Colorado orogeny extends into the High Plains. So I\nneed to search High Plains and find its elevation range.\nAction 3: Search[High Plains]\nObservation 3: High Plains refers to one of two distinct land regions\nThought 4: I need to instead search High Plains (United States).\nAction 4: Search[High Plains (United States)]\nObservation 4: The High Plains are a subregion of the Great Plains. From east to west, the\nHigh Plains rise in elevation from around 1,800 to 7,000 ft (550 to 2,130m).\nThought 5: High Plains rise in elevation from around 1,800 to 7,000 ft, so the answer\nis 1,800 to 7,000 ft.\nAction 5: Finish[1,800 to 7,000 ft]\n\nQuestion: Musician and satirist Allie Goertz wrote a song about the \"The Simpsons\"\ncharacter Milhouse, who Matt Groening named after who?\nThought 1: The question simplifies to \"The Simpsons\" character Milhouse is named after\nwho. I only need to search Milhouse and find who it is named after.\nAction 1: Search[Milhouse]\nObservation 1: Milhouse Mussolini Van Houten is a recurring character in the Fox animated\ntelevision series The Simpsons voiced by Pamela Hayden and created by Matt Groening.\nThought 2: The paragraph does not tell who Milhouse is named after, maybe I can look up\n\"named after\".\nAction 2: Lookup[named after]\nObservation 2: (Result 1 / 1) Milhouse was named after U.S. president Richard Nixon, whose\nmiddle name was Milhous.\nThought 3: Milhouse was named after U.S. president Richard Nixon, so the answer is\nRichard Nixon.\nAction 3: Finish[Richard Nixon]\n\nQuestion: Which documentary is about Finnish rock groups, Adam Clayton Powell or The Saimaa Gesture?\nThought 1: I need to search Adam Clayton Powell and The Saimaa Gesture, and find which\ndocumentary is about Finnish rock groups.\nAction 1: Search[Adam Clayton Powell]\nObservation 1 Could not find [Adam Clayton Powell]. Similar: [’Adam Clayton Powell\nIII’, ’Seventh Avenue (Manhattan)’, ’Adam Clayton Powell Jr. State Office\nBuilding’, ’Isabel Washington Powell’, ’Adam Powell’, ’Adam Clayton Powell\n(film)’, ’Giancarlo Esposito’].\nThought 2: To find the documentary, I can search Adam Clayton Powell (film).\nAction 2: Search[Adam Clayton Powell (film)]\nObservation 2: Adam Clayton Powell is a 1989 American documentary film directed by\nRichard Kilberg. The film is about the rise and fall of influential\nAfrican-American politician Adam Clayton Powell Jr.[3][4] It was later aired\nas part of the PBS series The American Experience.\nThought 3: Adam Clayton Powell (film) is a documentary about an African-American\npolitician, not Finnish rock groups. So the documentary about Finnish rock\ngroups must instead be The Saimaa Gesture.\nAction 3: Finish[The Saimaa Gesture]\n\nQuestion: What profession does Nicholas Ray and Elia Kazan have in common?\nThought 1: I need to search Nicholas Ray and Elia Kazan, find their professions, then\nfind the profession they have in common.\nAction 1: Search[Nicholas Ray]\nObservation 1: Nicholas Ray (born Raymond Nicholas Kienzle Jr., August 7, 1911 - June 16,\n1979) was an American film director, screenwriter, and actor best known for\nthe 1955 film Rebel Without a Cause.\nThought 2: Professions of Nicholas Ray are director, screenwriter, and actor. I need\nto search Elia Kazan next and find his professions.\nAction 2: Search[Elia Kazan]\nObservation 2: Elia Kazan was an American film and theatre director, producer, screenwriter\nand actor.\nThought 3: Professions of Elia Kazan are director, producer, screenwriter, and actor.\nSo profession Nicholas Ray and Elia Kazan have in common is director, screenwriter, and actor.\nAction 3: Finish[director, screenwriter, actor]\n\nQuestion: Which magazine was started first Arthur’s Magazine or First for Women?\nThought 1: I need to search Arthur’s Magazine and First for Women, and find which was started first.\nAction 1: Search[Arthur’s Magazine]\nObservation 1: Arthur’s Magazine (1844-1846) was an American literary periodical published\nin Philadelphia in the 19th century.\nThought 2: Arthur’s Magazine was started in 1844. I need to search First for Women next.\nAction 2: Search[First for Women]\nObservation 2: First for Women is a woman’s magazine published by Bauer Media Group in the\nUSA.[1] The magazine was started in 1989.\nThought 3: First for Women was started in 1989. 1844 (Arthur’s Magazine) < 1989 (First\nfor Women), so Arthur’s Magazine was started first.\nAction 3: Finish[Arthur’s Magazine]\n\nQuestion: Were Pavel Urysohn and Leonid Levin known for the same type of work?\nThought 1: I need to search Pavel Urysohn and Leonid Levin, find their types of work,\nthen find if they are the same.\nAction 1: Search[Pavel Urysohn]\nObservation 1: Pavel Samuilovich Urysohn (February 3, 1898 - August 17, 1924) was a Soviet\nmathematician who is best known for his contributions in dimension theory.\nThought 2: Pavel Urysohn is a mathematician. I need to search Leonid Levin next and\nfind its type of work.\nAction 2: Search[Leonid Levin]\nObservation 2: Leonid Anatolievich Levin is a Soviet-American mathematician and computer scientist.\nThought 3: Leonid Levin is a mathematician and computer scientist. So Pavel Urysohn\nand Leonid Levin have the same type of work.\nAction 3: Finish[yes]\nQuestion: Author David Chanoff has collaborated with a U.S. Navy admiral who served as the ambassador to the United Kingdom under which President?\nThought 1: I need to search David Chanoff, find the U.S. Navy admiral he collaborated with, and then find which President the admiral served as the ambassador to the United Kingdom under.\nAction 1: Search[David Chanoff]",)

  (def question "Author David Chanoff has collaborated with a U.S. Navy admiral who served as the ambassador to the United Kingdom under which President?")
  (import '[bosquet.agent.wikipedia Wikipedia])
  (solve-task (Wikipedia.) {:task question})

  (generate-thoughts
    {:reasoning-trace trace :observation
     "David Chanoff is a noted author of non-fiction work."
     :step 2}
    prompt-palette :react/step-n)

  #_(with-redefs [generator/complete (fn [_ctx _prompts prompt-keys]
                                       {:thoughts "I am thinking..."
                                        (first prompt-keys)
                                        "You are ReAct! I am thinking...\nAnd then again thinking"})]
      (generate-thoughts nil nil :test)))
