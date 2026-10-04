package com.multitasks.store

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.multitasks.store.barcode.BarcodeRenderer
import com.multitasks.store.model.*
import com.multitasks.store.viewmodel.CaptureViewModel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CaptureFlowTest {
    @get:Rule(order=0) val permission=GrantPermissionRule.grant(android.Manifest.permission.CAMERA)
    @get:Rule(order=1) val rule=createAndroidComposeRule<MainActivity>()
    private lateinit var capture: CaptureViewModel
    private fun screenshot(name: String){rule.waitForIdle();Thread.sleep(600);val file=File(rule.activity.filesDir,"review-v2/$name.png");file.parentFile!!.mkdirs();val bitmap=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    private fun productFrame(): Bitmap {
        val b=Bitmap.createBitmap(600,600,Bitmap.Config.ARGB_8888)
        val c=android.graphics.Canvas(b);c.drawColor(0xFFFCE7F1.toInt())
        val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color=android.graphics.Color.WHITE;c.drawRoundRect(170f,85f,430f,520f,24f,24f,p)
        p.color=0xFF558CC9.toInt();c.drawRoundRect(200f,50f,400f,125f,12f,12f,p);c.drawRect(170f,190f,430f,395f,p)
        p.color=android.graphics.Color.WHITE;p.textAlign=android.graphics.Paint.Align.CENTER;p.textSize=64f;p.typeface=android.graphics.Typeface.DEFAULT_BOLD;c.drawText("MILK",300f,285f,p)
        p.textSize=26f;c.drawText("SAMPLE",300f,337f,p)
        p.color=0xFF77727A.toInt();p.textSize=30f;c.drawText("200 ml",300f,464f,p)
        return b
    }
    @Test fun captureRescanSaveHistoryGalleryAndRealStockPhoto(){
        rule.runOnUiThread{capture=ViewModelProvider(rule.activity)[CaptureViewModel::class.java]}
        rule.onNodeWithTag("launch-QA").assertExists()
        rule.onNodeWithText("งานที่ต้องดูวันนี้").assertDoesNotExist()
        screenshot("01-home")
        rule.onNodeWithTag("launch-QA").performClick()
        rule.waitUntil(15000){capture.state.value.type==WorkType.QA&&capture.state.value.sessionId.isNotBlank()}
        // Inject the decoded frame boundary to test UI/state deterministically; ML Kit has its own decode test.
        rule.runOnUiThread{if(capture.state.value.phase==CapturePhase.CONFIRMING)capture.rescan();capture.quantity("1")}
        rule.runOnUiThread{capture.scanned("8851234567896","CODE_128",productFrame())}
        rule.waitUntil(15000){capture.state.value.phase==CapturePhase.CONFIRMING}
        rule.onNodeWithTag("เพิ่มจำนวน").performTouchInput{click()}
        rule.waitUntil{capture.state.value.quantity=="2"}
        rule.onNodeWithTag("quantity").performTextReplacement("24")
        rule.onNodeWithTag("quantity").performImeAction()
        val session=capture.state.value.sessionId
        val firstPhoto=capture.state.value.photo
        screenshot("02-qa-confirm")
        rule.activityRule.scenario.recreate()
        rule.runOnUiThread{capture=ViewModelProvider(rule.activity)[CaptureViewModel::class.java]}
        assertEquals("24",capture.state.value.quantity)
        assertEquals("8851234567896",capture.state.value.barcode)
        rule.onNodeWithTag("rescan").performClick()
        rule.waitUntil{capture.state.value.phase==CapturePhase.SCANNING}
        assertEquals("24",capture.state.value.quantity);assertEquals(session,capture.state.value.sessionId)
        rule.runOnUiThread{capture.scanned("1234567890128","CODE_128",productFrame())}
        rule.waitUntil(15000){capture.state.value.phase==CapturePhase.CONFIRMING}
        assertNotEquals(firstPhoto,capture.state.value.photo)
        rule.onNodeWithTag("barcode-result").assertTextEquals("1234567890128")
        val before=capture.state.value.savedCount
        rule.onNodeWithTag("save-record").performClick()
        rule.waitUntil(15000){capture.state.value.phase==CapturePhase.SCANNING&&capture.state.value.savedCount==before+1}
        assertEquals("1",capture.state.value.quantity);assertEquals(session,capture.state.value.sessionId)
        screenshot("03-qa-next")
        rule.onNodeWithContentDescription("ย้อนกลับ").performClick()
        rule.onNodeWithTag("launch-STOCK").performClick()
        rule.waitUntil(15000){rule.onAllNodesWithTag("take-photo").fetchSemanticsNodes().isNotEmpty()}
        rule.waitUntil(15000){runCatching{rule.onNodeWithTag("take-photo").assertIsEnabled()}.isSuccess}
        rule.onNodeWithTag("take-photo").performClick()
        rule.waitUntil(20000){capture.state.value.phase==CapturePhase.CONFIRMING}
        rule.onNodeWithTag("quantity").performTextReplacement("2")
        rule.onNodeWithTag("quantity").performImeAction()
        screenshot("04-stock-confirm")
        val stockBefore=capture.state.value.savedCount
        rule.onNodeWithTag("save-record").performClick()
        rule.waitUntil(15000){capture.state.value.phase==CapturePhase.SCANNING&&capture.state.value.savedCount==stockBefore+1}
        rule.onNodeWithContentDescription("ย้อนกลับ").performClick()
        rule.onNodeWithTag("launch-ประวัติ").performClick()
        rule.waitUntil(10000){rule.onAllNodesWithTag("history-search").fetchSemanticsNodes().isNotEmpty()}
        screenshot("05-history")
        rule.onNodeWithContentDescription("ดูแกลเลอรี").performClick()
        screenshot("06-gallery")
        val repo=(rule.activity.application as MultiTasksApp).repository
        val latest=repo.records.value.first{it.sessionId==capture.state.value.sessionId&&!it.deleted}
        rule.onNodeWithTag("record-${latest.id}").performScrollTo().performClick()
        screenshot("07-viewer")
        rule.onNodeWithTag("image-pager").performTouchInput{swipeLeft()}
        rule.onNodeWithContentDescription("เมนูรูปภาพ").performClick()
        rule.onNodeWithText("รายละเอียด").performClick()
        screenshot("08-detail")
    }
    @Test fun secondaryScreensAreReachableWithoutDuplicatedWorkTabs(){
        rule.onNodeWithContentDescription("ตั้งค่า").performClick()
        screenshot("09-settings")
        rule.onNodeWithText("คู่มือและเกี่ยวกับแอป").performScrollTo().performClick()
        screenshot("10-help")
        rule.onNodeWithContentDescription("ย้อนกลับ").performClick()
        rule.onNodeWithContentDescription("ย้อนกลับ").performClick()
        rule.onNodeWithTag("launch-สร้างงาน").performClick()
        screenshot("11-create")
        rule.onNodeWithContentDescription("ย้อนกลับ").performClick()
        rule.onNodeWithText("กล้อง").performClick()
        screenshot("12-quick-capture")
    }
}
