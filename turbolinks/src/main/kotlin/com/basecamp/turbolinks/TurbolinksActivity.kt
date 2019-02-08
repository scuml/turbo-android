package com.basecamp.turbolinks

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.NavController

abstract class TurbolinksActivity : AppCompatActivity(), TurbolinksFragment.OnFragmentListener {
    abstract val listener: Listener
    protected val view: ViewGroup by lazy { listener.onProvideView() }

    interface Listener {
        fun onActivityCreated()
        fun onProvideView(): ViewGroup
        fun onProvideProgressView(location: String): View
        fun onProvideErrorView(statusCode: Int): View
        fun onProvideNavController(): NavController
        fun onProvideCurrentDestination(): Fragment?
        fun onProvideNavigationAction(location: String): Int?
        fun onProvideSession(fragment: TurbolinksFragment): TurbolinksSession
        fun onDestinationTitleChanged(title: String)
        fun onDestinationReplaced()
        fun onNavigatedForward()
        fun onNavigatedBackward()
        fun onBackStackCleared()
        fun onRequestFullscreen()
        fun onRequestExitFullscreen()
    }

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(view)
        listener.onActivityCreated()
    }

    final override fun onSupportNavigateUp(): Boolean {
        detachWebViewFromCurrentDestination()
        return listener.onProvideNavController().navigateUp().also {
            listener.onNavigatedBackward()
        }
    }

    final override fun onBackPressed() {
        popBackStack()
    }

    final override fun onProvideSession(fragment: TurbolinksFragment): TurbolinksSession {
        return listener.onProvideSession(fragment)
    }

    final override fun onProvideProgressView(location: String): View {
        return listener.onProvideProgressView(location)
    }

    final override fun onProvideErrorView(errorStatusCode : Int): View {
        return listener.onProvideErrorView(errorStatusCode)
    }

    final override fun onDestinationTitleChanged(fragment: Fragment, title: String) {
        if (fragment == listener.onProvideCurrentDestination()) {
            listener.onDestinationTitleChanged(title)
        }
    }

    final override fun onRequestFullscreen() {
        listener.onRequestFullscreen()
    }

    final override fun onRequestExitFullscreen() {
        listener.onRequestExitFullscreen()
    }

    final override fun navigate(location: String, action: String) {
        detachWebViewFromCurrentDestination {
            val bundle = Bundle().apply { putString("location", location) }
            val controller = listener.onProvideNavController()

            if (action == "replace") {
                controller.popBackStack()
            }

            listener.onProvideNavigationAction(location)?.let { actionId ->
                controller.navigate(actionId, bundle)

                when (action) {
                    "replace" -> listener.onDestinationReplaced()
                    "advance" -> listener.onNavigatedForward()
                }
            }
        }
    }

    final override fun popBackStack() {
        detachWebViewFromCurrentDestination {
            when (listener.onProvideNavController().popBackStack()) {
                true -> listener.onNavigatedBackward()
                else -> finish()
            }
        }
    }

    fun isAtStartDestination(): Boolean {
        val controller = listener.onProvideNavController()
        return controller.graph.startDestination == controller.currentDestination?.id
    }

    fun clearBackStack() {
        if (isAtStartDestination()) return

        detachWebViewFromCurrentDestination {
            val controller = listener.onProvideNavController()
            controller.popBackStack(controller.graph.startDestination, false)
            listener.onBackStackCleared()
        }
    }

    /**
     * It's necessary to detach the shared WebView from a screen *before* it is hidden or exits and
     * the navigation animations run. The framework animator expects that the View hierarchy will
     * not change during the transition. Because the incoming screen will attach the WebView to the
     * new view hierarchy, it needs to already be detached from the previous screen.
     */
    private fun detachWebViewFromCurrentDestination(onDetached: () -> Unit = {}) {
        listener.onDestinationTitleChanged("")
        currentDestinationAction {
            when (it) {
                is TurbolinksFragment -> it.detachWebView(onDetached)
                else -> onDetached()
            }
        }
    }

    private fun currentDestinationAction(action: (Fragment) -> Unit) {
        listener.onProvideCurrentDestination()?.let(action)
    }
}