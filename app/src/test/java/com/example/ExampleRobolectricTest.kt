package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Secure Messenger", appName)
  }

  @Test
  fun `test viewModel initialization`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.ChatViewModel(context)
    org.junit.Assert.assertNotNull(viewModel)
  }

  @Test
  fun `test launcher background drawable loading`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_launcher_background)
    org.junit.Assert.assertNotNull(drawable)
  }

  @Test
  fun `test launcher foreground drawable loading`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
    org.junit.Assert.assertNotNull(drawable)
  }
}
