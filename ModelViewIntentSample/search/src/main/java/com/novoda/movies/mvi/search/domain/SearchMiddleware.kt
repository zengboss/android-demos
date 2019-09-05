package com.novoda.movies.mvi.search.domain

import com.novoda.movies.mvi.search.Middleware
import com.novoda.movies.mvi.search.data.MovieDataSource
import com.novoda.movies.mvi.search.domain.SearchReducer.Changes
import com.novoda.movies.mvi.search.domain.SearchReducer.Changes.*
import com.novoda.movies.mvi.search.presentation.SearchActivity.Action
import com.novoda.movies.mvi.search.presentation.SearchActivity.State
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.functions.BiFunction


internal class SearchMiddleware(
        private val dataSource: MovieDataSource,
        private val workScheduler: Scheduler
) : Middleware<Action, State, Changes> {

    override fun bind(actions: Observable<Action>, state: Observable<State>): Observable<Changes> {
        return actions
                .withLatestFrom(state, actionToState())
                .switchMap { (action, state) -> handle(action, state) }
    }

    private fun actionToState(): BiFunction<Action, State, Pair<Action, State>> =
            BiFunction { action, state -> action to state }

    private fun handle(action: Action, state: State): Observable<Changes> =
            when (action) {
                is Action.ChangeQuery -> Observable.just(UpdateSearchQuery(action.queryString))
                is Action.ExecuteSearch -> processAction(state)
                is Action.ClearQuery -> processClearQuery()
            }

    private fun processClearQuery(): Observable<Changes> {
        val updateQuery = Observable.just(UpdateSearchQuery("") as Changes)
        val removeResults = Observable.just(RemoveResults)
        return updateQuery.concatWith(removeResults)
    }

    private fun processAction(state: State): Observable<Changes> {
        val loadContent = dataSource.search(state.queryString)
                .toObservable()
                .map { searchResult -> AddResults(searchResult) as Changes }
                .startWith(ShowProgress)
                .onErrorReturn { throwable -> HandleError(throwable) }
        val hideProgress = Observable.just(HideProgress)

        return loadContent
                .concatWith(hideProgress)
                .subscribeOn(workScheduler)
    }
}

