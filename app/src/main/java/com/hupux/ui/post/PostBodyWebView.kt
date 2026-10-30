package com.hupux.ui.post

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.text.method.LinkMovementMethod
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.Image
import coil3.request.crossfade
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.hupux.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.lang.ref.WeakReference

// LocalImageClick 在 PostDetailScreen.kt 中定义（internal）

// ─── HtmlText ─────────────────────────────────────────────────────────────────

@Composable
fun HtmlText(html: String, imageScale: Float = 1.0f, modifier: Modifier = Modifier) {
    val textColor    = TextPrimary.toArgb()
    val context      = LocalContext.current
    val onImageClick = LocalImageClick.current
    // 用 BoxWithConstraints 拿可用宽度：图片尺寸要在 Html.fromHtml 那一刻就算好，
    // 那时 TextView 还没测量过（width == 0），只能靠 Compose 这边给。
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val availWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextColor(textColor)
                textSize = 15f
                setLineSpacing(0f, 1.65f)
            }
        },
        update = { tv ->
            val processedHtml = fixLazyImages(html)
            val getter  = CoilImageGetter(context, WeakReference(tv), imageScale, availWidthPx)
            val spanned = Html.fromHtml(processedHtml, Html.FROM_HTML_MODE_COMPACT, getter, null)
            // 给每个 ImageSpan 叠加 ClickableSpan，实现点击大图
            val sb = SpannableStringBuilder(spanned)
            sb.getSpans(0, sb.length, ImageSpan::class.java).forEach { imgSpan ->
                val start = sb.getSpanStart(imgSpan)
                val end   = sb.getSpanEnd(imgSpan)
                val src   = imgSpan.source?.let { if (it.startsWith("//")) "https:$it" else it }
                    ?: return@forEach
                sb.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) { onImageClick(src) }
                    override fun updateDrawState(ds: TextPaint) {} // 不改变样式
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            tv.text = sb
        },
        modifier = Modifier.fillMaxWidth()
    )
    }
}

// 将懒加载属性 data-src / data-original 替换为 src，使 Html.fromHtml 能找到图片 URL
internal fun fixLazyImages(html: String): String {
    if (!html.contains("<img", ignoreCase = true)) return html
    return try {
        val doc = Jsoup.parseBodyFragment(html)
        doc.select("img").forEach { img ->
            // data-gif 排在最前：虎扑给动图的 src 是 OSS 转出来的静态 jpg
            val actual = img.attr("data-gif").takeIf { it.isNotEmpty() }
                ?: img.attr("data-src").takeIf { it.isNotEmpty() }
                ?: img.attr("data-original").takeIf { it.isNotEmpty() }
                ?: img.attr("data-lazy-src").takeIf { it.isNotEmpty() }
            if (actual != null) img.attr("src", actual)
        }
        doc.body().html()
    } catch (_: Exception) {
        html
    }
}

// ─── Coil-backed ImageGetter for inline <img> in HTML ────────────────────────

private class CoilImageGetter(
    private val context: Context,
    private val tvRef: WeakReference<TextView>,
    private val imageScale: Float = 1.0f,
    private val availWidthPx: Int = 0
) : Html.ImageGetter {

    /**
     * `Html.ImageGetter` 是**同步**接口：`ImageSpan` 在这里就按返回 drawable 的 bounds 定尺寸。
     *
     * 以前的做法是先返回 1×1 占位、等图片下载完再改 bounds，但那时 TextView 已经按 1×1
     * 量过行高了，于是表情/图片显示不正常，非得等下一次重组（比如点开大图再退回来、
     * 这时图片已进内存缓存、尺寸能同步拿到）重建 span 才对——这正是「看一次大图就好了」的原因。
     *
     * 虎扑的图片 URL 里都带着原始尺寸（`..._o_w_86_h_70_xxx.GIF`），所以尺寸在这一刻
     * 就能算出来，根本不用等图片。
     */
    override fun getDrawable(source: String): Drawable {
        val url = if (source.startsWith("//")) "https:$source" else source
        val wrap = WrapDrawable()

        val w = targetWidth()
        // URL 里读不到尺寸时只能先按正方形占位，等图片下来再校正
        val ratio = ratioFromUrl(url)
        val h = (w * (ratio ?: 1f)).toInt().coerceAtLeast(1)
        wrap.setBounds(0, 0, w, h)

        context.imageLoader.enqueue(
            ImageRequest.Builder(context)
                .data(url)
                .target(
                    onSuccess = { image: Image ->
                        val tv = tvRef.get()
                        if (tv != null) {
                            // asDrawable 在 Coil 3 返回 BitmapDrawable（ARGB_8888），不用 hardware bitmap
                            val result = image.asDrawable(context.resources)
                            val realH = if (ratio != null) h else {
                                val iw = result.intrinsicWidth.coerceAtLeast(1)
                                val ih = result.intrinsicHeight.coerceAtLeast(1)
                                (ih.toFloat() / iw * w).toInt().coerceAtLeast(1)
                            }
                            result.setBounds(0, 0, w, realH)
                            wrap.inner = result
                            if (realH == wrap.bounds.height()) {
                                // 尺寸和占位一致，重画即可，不用重新排版
                                tv.invalidate()
                            } else {
                                wrap.setBounds(0, 0, w, realH)
                                tv.text = tv.text
                            }
                        }
                    }
                )
                .build()
        )
        return wrap
    }

    /** 正文可用宽度 × [imageScale]；Compose 没给宽度时退回屏宽估一个 */
    private fun targetWidth(): Int {
        val tv = tvRef.get()
        val baseW = when {
            availWidthPx > 0 -> availWidthPx
            tv != null && tv.width > 0 -> tv.width - tv.paddingLeft - tv.paddingRight
            else -> context.resources.displayMetrics.widthPixels - 64
        }
        return (baseW * imageScale).toInt().coerceAtLeast(1)
    }

    /** 从 URL 里的原始尺寸算高宽比；读不到返回 null */
    private fun ratioFromUrl(url: String): Float? {
        val m = SIZE_IN_URL.find(url) ?: return null
        val w = m.groupValues[1].toFloatOrNull() ?: return null
        val h = m.groupValues[2].toFloatOrNull() ?: return null
        return if (w > 0f && h > 0f) h / w else null
    }

    companion object {
        /** 虎扑图片 URL 里都带原始尺寸，形如 `..._w_86_h_70_xxx.GIF` / `..._w_690_h_1035_.webp` */
        private val SIZE_IN_URL = Regex("""_w_(\d+)_h_(\d+)[_.]""")
    }

    private class WrapDrawable : Drawable() {
        var inner: Drawable? = null
        override fun draw(c: Canvas)                     { inner?.draw(c) }
        override fun setAlpha(alpha: Int)                { inner?.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?)    { inner?.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSPARENT
        override fun getIntrinsicWidth()  = bounds.width()
        override fun getIntrinsicHeight() = bounds.height()
    }
}

// ─── WebView renderer for post body (handles images + <video>) ────────────────

@Composable
internal fun PostBodyWebView(html: String, modifier: Modifier = Modifier) {
    // heightState 作为稳定引用供 JS 接口捕获；用 rememberSaveable 持久化 dp 整型值，
    // 防止 LazyColumn 回收 item 后重进 composition 时高度重置为 200.dp 导致白色空档
    val heightState = rememberSaveable { mutableIntStateOf(200) }
    var heightDp by heightState
    val isDark       = isDarkTheme
    val textHex      = if (isDark) "#EBEBF0" else "#1A1A2E"
    val bgColor      = CardBg.toArgb()
    val onImageClick = LocalImageClick.current
    // 用 holder 稳定引用最新的回调（factory 只运行一次）
    val clickRef = remember { object { var fn: (String) -> Unit = {} } }
    clickRef.fn  = onImageClick

    AndroidView(
        factory = { ctx ->
            val wv = WebView(ctx)
            wv.apply {
                setBackgroundColor(bgColor)
                isScrollContainer = false
                isVerticalScrollBarEnabled = false
                settings.apply {
                    javaScriptEnabled = true
                    // 这两项必须关：开着时 WebView 会按「宽视口 + 缩放到屏宽」渲染，
                    // 页面 1 CSS px 就不再等于 1 dp，而 JS 量到的高度是 CSS px，
                    // 直接当 dp 用会把容器撑出一大片空白。关掉后布局宽度就是控件宽度，
                    // CSS px 与 dp 严格 1:1。
                    useWideViewPort = false
                    loadWithOverviewMode = false
                    setSupportZoom(false)
                    displayZoomControls = false
                    mediaPlaybackRequiresUserGesture = true
                }
                // JS → Kotlin 回调
                addJavascriptInterface(object : Any() {
                    /**
                     * @param cssPx 正文高度（CSS px）
                     * @param cssWidth 页面布局宽度（CSS px），用来把 cssPx 换算成 dp——
                     *        上面已保证 1:1，这里再按实际宽度校一次，
                     *        免得某些机型 / WebView 版本仍然自作主张缩放。
                     */
                    @JavascriptInterface
                    fun onHeight(cssPx: Int, cssWidth: Int) {
                        Handler(Looper.getMainLooper()).post {
                            val density = wv.resources.displayMetrics.density
                            val viewDp  = if (density > 0) wv.width / density else 0f
                            val scale   = if (cssWidth > 0 && viewDp > 0) viewDp / cssWidth else 1f
                            // Compose 布局最大约 262143 px；以 dp 计上限取 60000 留足余量
                            val h = (cssPx * scale + 24).toInt().coerceIn(1, 60000)
                            // 允许变矮：只增不减的话，任何一次偏大的测量都会永久留下空白。
                            // 4dp 阈值用来吃掉亚像素抖动，避免来回改高度。
                            if (kotlin.math.abs(h - heightState.intValue) > 4) {
                                heightState.intValue = h
                            }
                        }
                    }
                    @JavascriptInterface
                    fun onImgClick(rawUrl: String) {
                        val url = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
                        if (url.isNotEmpty()) Handler(Looper.getMainLooper()).post { clickRef.fn(url) }
                    }
                }, "App")
                // 阻止 WebView 内部跳转
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, url: String) = true
                }
            }
            wv
        },
        update = { wv ->
            // AndroidView.update 在每次 recomposition 后都会执行；
            // 用 tag 记录上次加载的页面 key，内容未变则跳过，防止 WebView 重载导致高度闪变
            val pageKey = "$html|$textHex|$bgColor"
            if (wv.tag as? String != pageKey) {
                wv.tag = pageKey
                val heightScript = """<script>
(function(){
  var sent=-1, timer=null;
  // 量到的高度会回写成容器高度，容器变高又会触发 ResizeObserver，
  // 所以去抖一下，等版面稳定了再报，免得图片加载途中的中间值被当成最终值。
  function flush(){
    var h=document.body.getBoundingClientRect().height||0;
    if(h<=0) h=document.body.scrollHeight||document.body.offsetHeight||0;
    h=Math.ceil(h);
    if(h>10&&h!==sent){ sent=h; App.onHeight(h, document.documentElement.clientWidth||0); }
  }
  function report(){ if(timer) clearTimeout(timer); timer=setTimeout(flush,120); }
  if(typeof ResizeObserver!=='undefined'){
    new ResizeObserver(report).observe(document.body);
  }
  document.querySelectorAll('img').forEach(function(img){
    // 虎扑图片 URL 自带原始尺寸（..._w_2000_h_1333_.png），据此先占好位，
    // 图片加载前后版面高度就不会来回跳，量到的高度一开始就是对的
    var m=/_w_(\d+)_h_(\d+)_/.exec(img.getAttribute('src')||'');
    if(m&&+m[1]>0&&+m[2]>0) img.style.aspectRatio=m[1]+' / '+m[2];
    img.addEventListener('load', report);
    img.addEventListener('error', report);
    img.style.cursor='pointer';
    img.addEventListener('click', function(e){
      e.stopPropagation();
      var url=img.getAttribute('src')||img.getAttribute('data-src')||'';
      if(url) App.onImgClick(url);
    });
  });
  [0,200,600,1500,3500].forEach(function(t){ setTimeout(report,t); });
})();
</script>"""
                val page = """<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<style>
body{background:transparent;color:$textHex;font-size:15px;line-height:1.65;
     margin:0;padding:0;word-break:break-word}
p{margin:6px 0}a{color:#EA0E20}
img{max-width:100%;height:auto;display:block;margin:8px 0;border-radius:4px}
.img-grid{display:flex;flex-wrap:wrap;gap:4px;margin:8px 0}
.img-grid img{width:calc(33.33% - 3px);height:auto;border-radius:4px;
              display:block;flex-shrink:0;object-fit:contain;max-width:none;margin:0}
video{width:100%;height:auto;border-radius:4px;margin:8px 0;display:block}
</style></head><body>$html$heightScript</body></html>"""
                wv.loadDataWithBaseURL("https://m.hupu.com/", page, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier.fillMaxWidth().height(heightDp.dp)
    )
}
