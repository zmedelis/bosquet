(ns bosquet.db.vector-db)

(defprotocol VectorDB
  (create [_this _collection-name])
  (delete [_this _collection-name])
  (add [_this _collection-name _docs])
  (search [_this _collection-name _query _limit]))
