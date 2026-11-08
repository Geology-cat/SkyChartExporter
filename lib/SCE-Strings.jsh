// ----------------------------------------------------------------------------
// SkyChartExporter - SCE-Strings.jsh
//
// UI と PDF 出力の文言（日本語 / 英語）。
// ----------------------------------------------------------------------------

#ifndef __SCE_Strings_jsh
#define __SCE_Strings_jsh

var SCE_STRINGS = {
   // ---- ダイアログ全般 ----
   dialogTitle:        ["SkyChartExporter — 星図付き撮影記録PDF", "SkyChartExporter — Annotated Sky Chart PDF"],
   help:               ["プレートソルブ済み画像から、アノテーション画像・星図・基本データ・メモを1枚の横長PDFにまとめます。",
                        "Exports a plate-solved image as a one-page landscape PDF with annotations, a sky chart, basic data and notes."],
   language:           ["言語 / Language", "Language / 言語"],
   targetImage:        ["対象画像", "Target image"],
   noSolution:         ["この画像には天測解（プレートソルブ結果）がありません。ImageSolver などで先にプレートソルブしてください。",
                        "This image has no astrometric solution. Please plate-solve it first (e.g. with ImageSolver)."],
   noImage:            ["画像が開かれていません。", "No image is open."],

   grpDocument:        ["ドキュメント", "Document"],
   title:              ["タイトル", "Title"],
   titleTip:           ["空欄なら写っている主な天体名から自動で付けます。", "Leave empty to generate a title from the main object in the field."],
   observer:           ["撮影者", "Observer"],
   site:               ["撮影地", "Location"],
   pageSize:           ["用紙", "Page size"],
   theme:              ["配色", "Theme"],
   themeLight:         ["ライト（印刷向き）", "Light (print friendly)"],
   themeDark:          ["ダーク", "Dark"],
   font:               ["フォント", "Font"],
   fontAuto:           ["自動（日本語対応フォント）", "Automatic (CJK capable)"],

   grpImage:           ["画像と圧縮", "Image & compression"],
   stretch:            ["表示ストレッチ", "Display stretch"],
   stretchAuto:        ["自動（STF → リニアなら自動STF）", "Automatic (STF, or AutoSTF if linear)"],
   stretchNone:        ["なし（ストレッチ済み画像）", "None (already stretched)"],
   stretchSTF:         ["現在のSTFを適用", "Apply current STF"],
   stretchAutoSTF:     ["自動STFを適用", "Apply AutoSTF"],
   imageDpi:           ["画像の解像度 (dpi)", "Image resolution (dpi)"],
   autoCompress:       ["重い場合は自動圧縮する", "Compress automatically if too large"],
   maxSize:            ["目標サイズ上限 (MB)", "Target max size (MB)"],
   focalOverride:      ["焦点距離 (mm, 0=自動)", "Focal length (mm, 0=auto)"],
   pixelOverride:      ["画素サイズ (µm, 0=自動)", "Pixel size (µm, 0=auto)"],

   grpAnnot:           ["アノテーション（写真への書き込み）", "Annotations (on the photo)"],
   annotLevel:         ["書き込み量", "Amount"],
   lvNone:             ["なし", "None"],
   lvMinimal:          ["最小（主要天体のみ）", "Minimal (major objects)"],
   lvStandard:         ["標準", "Standard"],
   lvDetailed:         ["詳細", "Detailed"],
   lvAll:              ["すべて", "Everything"],
   lvCustom:           ["カスタム", "Custom"],
   aMessier:           ["メシエ天体", "Messier objects"],
   aCaldwell:          ["コールドウェル天体", "Caldwell objects"],
   aNamed:             ["通称のある天体", "Named objects"],
   aNGC:               ["NGC / IC", "NGC / IC"],
   aNGCMag:            ["NGC/IC の限界等級", "NGC/IC magnitude limit"],
   aSNR:               ["超新星残骸", "Supernova remnants"],
   aBrightNeb:         ["散光星雲", "Bright nebulae"],
   aBrightNebTip:      ["Sh2・LBN・vdB・RCW・Ced・DWB・vdBH", "Sh2, LBN, vdB, RCW, Ced, DWB, vdBH"],
   aDarkNeb:           ["暗黒星雲", "Dark nebulae"],
   aDarkNebTip:        ["Barnard・LDN", "Barnard, LDN"],
   aPN:                ["惑星状星雲", "Planetary nebulae"],
   aPNTip:             ["PK・PN G（NGC/IC 以外の小さな惑星状星雲を含む）", "PK, PN G (including small non-NGC/IC objects)"],
   aClusters:          ["散開星団", "Open clusters"],
   aClustersTip:       ["Collinder・Melotte・Trumpler・Stock・Ruprecht・vdB-Ha", "Collinder, Melotte, Trumpler, Stock, Ruprecht, vdB-Ha"],
   aGalaxies:          ["銀河", "Galaxies"],
   aGalaxiesTip:       ["PGC・UGC・ESO・Arp・VV・HCG（限界等級で絞り込み）", "PGC, UGC, ESO, Arp, VV, HCG (filtered by magnitude limit)"],
   aGalaxyMag:         ["銀河の限界等級", "Galaxy magnitude limit"],
   aGalaxyClusters:    ["銀河団", "Galaxy clusters"],
   aGalaxyClustersTip: ["Abell 銀河団", "Abell clusters"],
   catNeedsStel:       ["※ NGC/IC 以外のカタログは Stellarium のカタログ使用時のみ", "* Catalogs other than NGC/IC require the Stellarium catalog"],
   aStars:             ["恒星名", "Star names"],
   aStarMag:           ["恒星の限界等級", "Star magnitude limit"],
   aConstLines:        ["星座線", "Constellation lines"],
   aConstBorders:      ["星座境界", "Constellation borders"],
   aConstNames:        ["星座名", "Constellation names"],
   aGrid:              ["座標グリッド", "Coordinate grid"],
   aCompass:           ["方位とスケールバー", "Compass & scale bar"],
   aLabelNames:        ["ラベルに通称を併記", "Show common names in labels"],
   aLabelSize:         ["ラベル文字サイズ (pt)", "Label size (pt)"],

   grpChart:           ["星図（天球上の位置）", "Sky chart (location on the sky)"],
   chartFov:           ["星図の範囲", "Chart field"],
   fovAuto:            ["自動", "Automatic"],
   fovFactor:          ["画像視野の倍率", "Multiple of image FOV"],
   fovFixed:           ["角度で指定", "Fixed angle"],
   chartFactor:        ["倍率 (×)", "Factor (×)"],
   chartDeg:           ["範囲 (°)", "Field (°)"],
   chartStarMag:       ["恒星の限界等級 (0=自動)", "Star mag. limit (0=auto)"],
   cDSO:               ["星雲・星団・銀河", "Deep-sky objects"],
   cConstLines:        ["星座線", "Constellation lines"],
   cConstBorders:      ["星座境界", "Constellation borders"],
   cConstNames:        ["星座名", "Constellation names"],
   cGrid:              ["座標グリッド", "Coordinate grid"],

   grpList:            ["天体一覧", "Object list"],
   listMax:            ["最大件数", "Max. entries"],
   listStars:          ["恒星（固有名あり）も含める", "Include named stars"],

   grpMemo:            ["メモ（PDFに記載）", "Notes (printed on the PDF)"],

   grpCatalog:         ["カタログ", "Catalogs"],
   useStellarium:      ["Stellarium の天体カタログを使う（約9.5万天体）", "Use the Stellarium DSO catalog (~95,000 objects)"],
   stellariumDir:      ["Stellarium の場所", "Stellarium location"],
   stellariumDirTip:   ["空欄なら自動で探します（Stellarium.app、または nebulae フォルダを含むフォルダ）",
                        "Leave empty to search automatically (Stellarium.app or the folder containing 'nebulae')"],
   stelFound:          ["検出: %s", "Found: %s"],
   stelNotFound:       ["見つかりません（PixInsight 同梱カタログを使います）", "Not found (PixInsight catalogs will be used)"],
   useGaia:            ["星図の恒星に Gaia (XPSD) を使う", "Use Gaia (XPSD) stars in the sky chart"],
   gaiaDir:            ["Gaia XPSD フォルダ", "Gaia XPSD folder"],
   gaiaDirTip:         ["空欄なら PixInsight の Gaia プロセスの設定を使います（SPCC 用に設定済みならそのまま使えます）",
                        "Leave empty to use the Gaia process settings (already configured if you use SPCC)"],
   browse:             ["…", "…"],
   imageInfo:          ["画像情報", "Image info"],
   infoScale:          ["画素スケール %.2f″/px", "Scale %.2f″/px"],
   infoCamera:         ["カメラ: %s", "Camera: %s"],
   infoNoCamera:       ["カメラ名の記録なし", "No camera name recorded"],
   infoPixel:          ["画素サイズ: %s µm（%s）", "Pixel size: %s µm (%s)"],
   infoNoPixel:        ["画素サイズ: 不明（焦点距離は視野から35mm判換算で推定）", "Pixel size: unknown (focal length estimated from FOV, 35 mm equiv.)"],
   infoFocalApprox:    ["焦点距離: 約 %.0f mm（35mm判換算）", "Focal length: ≈ %.0f mm (35 mm equiv.)"],
   infoFocal:          ["焦点距離: %.0f mm", "Focal length: %.0f mm"],
   srcHeader:          ["画像の記録", "from image header"],
   srcCamera:          ["カメラ名から推定", "estimated from camera"],
   srcRemembered:      ["前回の入力値", "remembered"],
   srcManual:          ["手入力", "manual"],
   noSolutionShort:    ["天測解なし", "No astrometric solution"],

   preview:            ["プレビュー", "Preview"],
   exportPdf:          ["PDFを書き出す…", "Export PDF…"],
   close:              ["閉じる", "Close"],
   saveTitle:          ["PDFの保存先", "Save PDF as"],
   working:            ["処理中…", "Working…"],
   exported:           ["PDFを書き出しました", "PDF exported"],
   exportFailed:       ["PDFの書き出しに失敗しました", "PDF export failed"],
   previewTitle:       ["プレビュー", "Preview"],
   sizeOver:           ["目標サイズに収まりませんでした（画像以外の要素が大きい可能性があります）。",
                        "Could not reach the target size (non-image content may be large)."],

   // ---- PDF 出力 ----
   secInfo:            ["基本データ", "Basic data"],
   secList:            ["写っている主な天体", "Objects in the field"],
   secMemo:            ["メモ", "Notes"],
   secChart:           ["天球上の位置", "Location on the sky"],
   rRA:                ["赤経", "RA"],
   rDec:               ["赤緯", "Dec"],
   rConst:             ["星座", "Constellation"],
   rFov:               ["視野", "Field of view"],
   rScale:             ["画素スケール", "Image scale"],
   rFocal:             ["焦点距離", "Focal length"],
   rRotation:          ["回転角", "Rotation"],
   rSize:              ["画像サイズ", "Image size"],
   rDate:              ["撮影日時", "Date"],
   rExposure:          ["露出", "Exposure"],
   rTelescope:         ["鏡筒", "Telescope"],
   rCamera:            ["カメラ", "Camera"],
   rFilter:            ["フィルター", "Filter"],
   rSite:              ["撮影地", "Location"],
   rObserver:          ["撮影者", "Observer"],
   focalManual:        ["（手入力）", " (manual)"],
   focalEffective:     ["実効", "effective"],
   focalFromScale:     ["（画素スケールから算出）", " (from image scale)"],
   focalFromHeaderPx:  ["（画素 %s µm と画素スケールから算出）", " (from %s µm pixels and image scale)"],
   focalFromCamera:    ["（%s の画素 %s µm から推定）", " (estimated: %s, %s µm pixels)"],
   focalFromRemembered:["（画素 %s µm〔前回の入力値〕から算出）", " (from %s µm pixels, remembered)"],
   focalFromManualPx:  ["（画素 %s µm〔手入力〕から算出）", " (from %s µm pixels, manual)"],
   focalUnknown:       ["不明", "unknown"],
   focalFromFov:       ["（35mm判換算・視野から推定）", " (35 mm equivalent, from the field of view)"],
   approx:             ["約 ", "≈ "],
   catCredit:          ["天体カタログ: Stellarium", "DSO catalog: Stellarium"],
   flipped:            ["（鏡像）", " (mirrored)"],
   moreObjects:        ["ほか %d 天体", "%d more"],
   noObjects:          ["該当する天体はありません", "No catalogued objects"],
   chartField:         ["範囲 %s", "Field %s"],
   chartMag:           ["%s等星まで", "Stars to mag %s"],
   frameLabel:         ["撮影範囲", "Image field"],
   footerMade:         ["作成", "Created"],
   north:              ["N", "N"],
   east:               ["E", "E"],

   // ---- エラー ----
   errCenter:          ["画像中心で天測解を評価できません。プレートソルブの結果を確認してください。",
                        "The astrometric solution cannot be evaluated at the image center."],
   errPdfWrite:        ["PDFファイルを書き込めませんでした: ", "The PDF file could not be written: "],
   errCatalog:         ["カタログファイルが見つかりません: ", "Catalog file not found: "]
};

/*
 * 翻訳文への値の差し込み。%s・%d・%.Nf を順に置き換える。
 * PixInsight の format() は「%s」の直後に全角文字が続くと失敗するため、
 * 翻訳文にはこちらを使う。
 */
function sceFmt( template, ...args )
{
   let i = 0;
   return ("" + template).replace( /%(\.(\d+))?([sdf])/g, ( m, p, prec, kind ) =>
   {
      let v = args[i++];
      if ( kind == "f" )
         return Number( v ).toFixed( (prec !== undefined) ? parseInt( prec, 10 ) : 6 );
      if ( kind == "d" )
         return "" + Math.round( Number( v ) );
      return "" + v;
   } );
}

// 現在の言語（エクスポーターが設定する）。sceTr の既定値に使う。
var SCE_CURRENT_LANG = "ja";

function sceTr( key, lang = SCE_CURRENT_LANG )
{
   let e = SCE_STRINGS[key];
   if ( !e )
      return key;
   return (lang == "en") ? e[1] : e[0];
}

#endif   // __SCE_Strings_jsh
