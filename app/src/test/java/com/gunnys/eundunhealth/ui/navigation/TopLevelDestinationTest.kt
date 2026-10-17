package com.gunnys.eundunhealth.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun `child routes keep their parent destination selected`() {
        assertEquals(TopLevelDestination.TODAY, Screen.WorkoutDetail.route.topLevelDestination())
        assertEquals(TopLevelDestination.PROGRESS, Screen.Statistics.route.topLevelDestination())
        assertEquals(TopLevelDestination.PROGRESS, Screen.History.route.topLevelDestination())
        assertEquals(TopLevelDestination.PROGRESS, Screen.Goal.route.topLevelDestination())
        assertEquals(TopLevelDestination.PROGRESS, Screen.Badges.route.topLevelDestination())
        assertEquals(TopLevelDestination.PROFILE, Screen.ProfileEdit.route.topLevelDestination())
    }

    @Test
    fun `top level routes resolve to their own destination`() {
        TopLevelDestination.entries.forEach { destination ->
            assertEquals(destination, destination.route.topLevelDestination())
        }
    }
}
