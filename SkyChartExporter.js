// ----------------------------------------------------------------------------
// SkyChartExporter.js
//
// プレートソルブ済み画像から、次の内容を1枚の横長PDFにまとめて書き出す
// PixInsight スクリプト。
//
//   ・アノテーションを書き込んだ画像（書き込み量はプリセット/個別に選択）
//   ・天球上の位置を示す星図（撮影範囲を枠で表示、範囲は自由に指定）
//   ・基本データ（中心座標・星座・視野・焦点距離・回転角・撮影データ）
//   ・写っている天体とその種類の一覧
//   ・メモ（ダイアログで記入）
//
// 写真以外はベクターで描くため軽量。写真が重い場合は目標サイズに収まるまで
// 自動で解像度を下げる。日本語/英語に対応（天体名は日本語名があれば日本語）。
//
// 必要環境: PixInsight 1.9.6 以降（V8 エンジン、PDF オブジェクト）
// ----------------------------------------------------------------------------

#engine v8

#feature-id    SkyChartExporter : Utilities > SkyChartExporter
#feature-info  Exports a plate-solved image as a lightweight one-page landscape PDF \
               with an annotated photo, a sky chart showing the field, basic data, \
               a list of catalogued objects and user notes. Japanese / English.

#iflt __PI_BUILD__ 1707
#error SkyChartExporter requires PixInsight 1.9.6 or later.
#endif


#define SCE_VERSION_STR "1.1.0"
var SCE_VERSION = SCE_VERSION_STR;

#include "lib/SCE-Names.jsh"
#include "lib/SCE-Strings.jsh"
#include "lib/SCE-Astro.jsh"
#include "lib/SCE-Inflate.jsh"
#include "lib/SCE-Stellarium.jsh"
#include "lib/SCE-Cameras.jsh"
#include "lib/SCE-Catalog.jsh"
#include "lib/SCE-ImageData.jsh"
#include "lib/SCE-Options.jsh"
#include "lib/SCE-Render.jsh"
#include "lib/SCE-Dialog.jsh"

/*
 * 処理の本体（ダイアログからも、テスト用の自動実行からも使う）
 */
class SceExporter
{
   constructor()
   {
      this.opts = new SceOptions();
      this.catalog = new SceCatalog();
      this.photoCache = null;
   }

   buildModel( window )
   {
      SCE_CURRENT_LANG = this.opts.lang;
      if ( window === null || window.isNull )
         throw new Error( sceTr( "noImage", this.opts.lang ) );
      if ( !window.hasAstrometricSolution )
         throw new Error( sceTr( "noSolution", this.opts.lang ) );
      this.catalog.load( { useStellarium: this.opts.useStellarium, stellariumDir: this.opts.stellariumDir } );
      let model = new SceModel( window, this.catalog, this.opts );
      // 画素サイズを手入力したら、そのカメラの値として覚えておく
      let cam = model.info.camera;
      if ( cam && this.opts.pixelOverride > 0 && this.opts.cameraPixelSizes[cam] !== this.opts.pixelOverride )
      {
         this.opts.cameraPixelSizes[cam] = this.opts.pixelOverride;
         this.opts.save();
      }
      return model;
   }

   /*
    * 長辺 longPx の写真ビットマップ。いちばん大きく要求されたものを
    * キャッシュし、小さい要求は縮小で応じる。
    */
   photo( window, longPx )
   {
      let image = window.mainView.image;
      let nativeLong = Math.max( image.width, image.height );
      longPx = Math.min( Math.round( longPx ), nativeLong );
      let key = window.mainView.uniqueId + "|" + this.opts.stretch + "|" + image.width + "x" + image.height;
      let c = this.photoCache;
      if ( c === null || c.key != key || c.longPx < longPx )
      {
         console.writeln( format( "<end><cbr>SkyChartExporter: rendering photo (%d px)...", longPx ) );
         processEvents();
         let bmp = sceRenderPhoto( window, longPx, this.opts.stretch );
         c = this.photoCache = { key: key, longPx: Math.max( bmp.width, bmp.height ), bmp: bmp };
      }
      if ( c.longPx <= longPx + 1 )
         return c.bmp;
      let s = longPx/c.longPx;
      return c.bmp.scaledTo( Math.max( 1, Math.round( c.bmp.width*s ) ), Math.max( 1, Math.round( c.bmp.height*s ) ) );
   }

   exportPDF( window, filePath )
   {
      let model = this.buildModel( window );
      let t0 = Date.now();
      let r = sceWritePDF( filePath, model, longPx => this.photo( window, longPx ) );
      console.writeln( format( "<end><cbr>SkyChartExporter: %s (%.2f MB, %.1f s)", filePath, r.size/1048576, (Date.now() - t0)/1000 ) );
      return r;
   }

   previewBitmap( window, widthPx )
   {
      let model = this.buildModel( window );
      let renderer = new SceRenderer( model );
      let dpi = widthPx/(renderer.W/25.4);
      let ps = renderer.photoSizeMM();
      let photo = this.photo( window, Math.max( ps.w, ps.h )/25.4*dpi*1.5 );
      return sceRenderPreview( model, photo, widthPx );
   }
}

function sceMain()
{
   console.abortEnabled = true;
   let exporter = new SceExporter();
   exporter.opts.load();

   let window = ImageWindow.activeWindow;
   if ( window.isNull )
   {
      (new MessageBox( sceTr( "noImage", exporter.opts.lang ), "SkyChartExporter", StdIcon.Error, StdButton.Ok )).execute();
      return;
   }

   let view = window.mainView;
   for ( ;; )
   {
      let dialog = new SceDialog( exporter, view );
      dialog.execute();
      try
      {
         let v = dialog.view_List.currentView;
         if ( v && !v.isNull )
            view = v;
      }
      catch ( x )
      {
      }
      exporter.opts.save();
      if ( !dialog.restart )
         break;
   }
}

#ifndef SCE_NO_MAIN
sceMain();
#endif

// ----------------------------------------------------------------------------
// EOF SkyChartExporter.js
