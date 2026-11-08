// ----------------------------------------------------------------------------
// SkyChartExporter - SCE-Render.jsh
//
// ページの割付と描画。写真以外（注記・星図・文字）はすべてベクターで描くので、
// PDF のサイズは実質的に写真の画素数だけで決まる。
// 描画は Graphics を介して行うため、同じコードで PDF とプレビュー用
// ビットマップの両方に描ける（座標はすべて mm → px に換算して渡す）。
// ----------------------------------------------------------------------------

#ifndef __SCE_Render_jsh
#define __SCE_Render_jsh

// 用紙サイズ（横向き, mm）
var SCE_PAGE_SIZES = {
   A4:     { w: 297,   h: 210,   ja: "A4 横",            en: "A4 landscape" },
   A3:     { w: 420,   h: 297,   ja: "A3 横",            en: "A3 landscape" },
   Letter: { w: 279.4, h: 215.9, ja: "レター 横",        en: "Letter landscape" },
   Wide:   { w: 297,   h: 167.06, ja: "ワイド 16:9",     en: "Wide 16:9" }
};
var SCE_PAGE_KEYS = [ "A4", "A3", "Letter", "Wide" ];

function sceARGB( rgb, alpha = 255 )
{
   return (alpha & 0xff)*0x1000000 + (rgb & 0xffffff);
}

var SCE_THEMES = {
   light: {
      page: 0xFFFFFF, text: 0x1E2228, sub: 0x6A707A, line: 0xD3D7DD, panel: 0xF4F6F9, accent: 0x2B5FA8,
      chartBg: 0xFFFFFF, chartStar: 0x15181C, chartGrid: 0xC5CBD3, chartConst: 0x5577BB, chartBorder: 0xB9A0BE,
      chartConstName: 0x56688F, chartFrame: 0xD3262F, chartText: 0x3A3F47, chartEdge: 0x9AA1AB,
      dso: { galaxy: 0xC2571A, nebula: 0xB8262E, pn: 0x0E7C86, oc: 0x8A6D00, gc: 0x9B2F8F, snr: 0xC0336E, dark: 0x707070, other: 0x555B63 }
   },
   dark: {
      page: 0x15181D, text: 0xE9EBEE, sub: 0x9EA4AD, line: 0x353B45, panel: 0x1E232A, accent: 0x7FB0FF,
      chartBg: 0x0B1022, chartStar: 0xF2F4F8, chartGrid: 0x2C3657, chartConst: 0x5D7CC9, chartBorder: 0x6B5A78,
      chartConstName: 0x8FA2D6, chartFrame: 0xFF4B55, chartText: 0xB8C0CF, chartEdge: 0x4A5470,
      dso: { galaxy: 0xFFB26B, nebula: 0xFF7F86, pn: 0x6EE7E7, oc: 0xFFE45C, gc: 0xF5A8EC, snr: 0xFF9EC4, dark: 0xA0A0A0, other: 0xC8CDD5 }
   }
};

// 写真の上の注記色（写真は暗いので常に明るい色）
var SCE_PHOTO_COLORS = {
   galaxy: 0xFFC266, nebula: 0xFF8E8E, pn: 0x72EAEA, oc: 0xFFE45C, gc: 0xFFB8F2, snr: 0xFFA3C8, dark: 0xC8C8C8, other: 0xE6E6E6,
   star: 0xAED0FF, constLine: 0x84A2FF, border: 0xC9A6C9, grid: 0xFFFFFF, constName: 0xB3C2FF, compass: 0xFFFFFF
};

function sceTypeGroup( type )
{
   switch ( type )
   {
   case "galaxy": case "galaxyPair": case "galaxyTriplet": case "galaxyGroup":
   case "interactingGalaxy": case "quasar": case "galaxyCluster":
      return "galaxy";
   case "nebula": case "emissionNebula": case "hii": case "reflectionNebula": case "clusterNebula":
      return "nebula";
   case "planetaryNebula":
      return "pn";
   case "openCluster": case "association": case "asterism": case "starCloud": case "doubleStar": case "starCluster":
      return "oc";
   case "globularCluster":
      return "gc";
   case "snr":
      return "snr";
   case "darkNebula": case "molecularCloud":
      return "dark";
   }
   return "other";
}

/*
 * 日本語を表示できるフォントファミリーを選ぶ
 */
function sceResolveFontFamily( preferred )
{
   if ( preferred && preferred.length > 0 )
      return preferred;
   let available = [];
   try
   {
      available = Font.families( WritingSystem.Japanese );
   }
   catch ( x )
   {
   }
   let wanted = [ "Hiragino Sans", "Hiragino Kaku Gothic ProN", "Hiragino Kaku Gothic Pro",
                  "Yu Gothic UI", "Yu Gothic", "YuGothic", "Meiryo UI", "Meiryo", "BIZ UDPGothic", "BIZ UDGothic",
                  "Noto Sans CJK JP", "Noto Sans JP", "Source Han Sans JP", "IPAexGothic", "IPAPGothic",
                  "TakaoPGothic", "VL PGothic", "MS PGothic" ];
   for ( let f of wanted )
      if ( available.indexOf( f ) >= 0 )
         return f;
   return (available.length > 0) ? available[0] : "";
}

/*
 * 天体の絞り込み（アノテーション設定）
 */
function sceMagPasses( o, limit )
{
   if ( limit >= 20 )
      return true;
   if ( o.mag !== undefined )
      return o.mag <= limit;
   // 等級のない天体（主に散光星雲）は大きさで判断
   return (o.diam || 0) >= Math.max( 1, 16 - limit );
}

function sceDsoPasses( o, opts )
{
   if ( o.m && opts.aMessier )
      return true;
   if ( o.c && opts.aCaldwell )
      return true;
   if ( (o.ja || o.en) && opts.aNamed )
      return true;
   if ( o.ngc && opts.aNGC && sceMagPasses( o, opts.aNGCMag ) )
      return true;
   if ( o.isSNR && opts.aSNR )
      return true;
   let ids = o.ids;
   if ( !ids )
      return false;
   if ( opts.aBrightNeb && (ids.Sh2 || ids.LBN || ids.VdB || ids.RCW || ids.Ced || ids.DWB || ids.VdBH) )
      return true;
   if ( opts.aDarkNeb && (ids.B || ids.LDN) )
      return true;
   if ( opts.aPN && (ids.PK || ids.PNG) )
      return true;
   if ( opts.aClusters && (ids.Cr || ids.Mel || ids.Tr || ids.St || ids.Ru || ids.VdBHa) )
      return true;
   if ( opts.aGalaxies && (ids.PGC || ids.UGC || ids.ESO || ids.Arp || ids.VV || ids.HCG) && sceMagPasses( o, opts.aGalaxyMag ) )
      return true;
   if ( opts.aGalaxyClusters && ids.ACO )
      return true;
   return false;
}

function sceAnyObjectLayer( opts )
{
   return opts.aMessier || opts.aCaldwell || opts.aNamed || opts.aNGC || opts.aSNR || opts.aBrightNeb ||
          opts.aDarkNeb || opts.aPN || opts.aClusters || opts.aGalaxies || opts.aGalaxyClusters;
}

function sceAnnotationsEnabled( opts )
{
   return sceAnyObjectLayer( opts ) || opts.aStars ||
          opts.aConstLines || opts.aConstBorders || opts.aConstNames || opts.aGrid || opts.aCompass;
}

// ----------------------------------------------------------------------------

/*
 * ページに載せる内容（DPI に依存しないデータ）
 */
class SceModel
{
   constructor( window, catalog, opts )
   {
      this.opts = opts;
      this.lang = opts.lang;
      this.catalog = catalog;
      this.info = new SceImageInfo( window );
      this.info.resolveFocal( opts );
      this.constellation = catalog.findConstellation( this.info.ra, this.info.dec );
      this.buildObjects();
      this.chartFov = this.computeChartFov();
      this.computeChartStars();
      this.title = (opts.title || "").trim() || this.autoTitle();
      this.infoRows = this.buildInfoRows();
   }

   buildObjects()
   {
      let info = this.info, opts = this.opts;
      let sizeDeg = o => (o.diam || 0)/60;

      this.allDSO = info.findInImage( this.catalog.dso, sizeDeg );
      let snrInImage = opts.aSNR ? info.findInImage( this.catalog.snr, sizeDeg ) : [];

      let byPriority = ( a, b ) => (a.o.rank - b.o.rank) || (a.o.sortMag - b.o.sortMag);

      this.annotDSO = this.allDSO.filter( e => sceDsoPasses( e.o, opts ) ).concat( snrInImage );
      this.annotDSO.sort( byPriority );

      let starLimit = Math.max( opts.aStars ? opts.aStarMag : 0, opts.listStars ? 4.5 : 0 );
      let stars = info.findInImage( this.catalog.stars.filter( s => s.mag <= starLimit ), null );
      stars.sort( ( a, b ) => a.o.mag - b.o.mag );
      this.annotStars = opts.aStars ? stars.filter( e => e.o.mag <= opts.aStarMag ) : [];

      // 一覧: アノテーション設定で天体を選んでいなければ標準の条件を使う
      let listFilter = sceAnyObjectLayer( opts ) ?
            ( o => sceDsoPasses( o, opts ) ) :
            ( o => sceDsoPasses( o, SCE_PRESETS.standard ) );
      let list = this.allDSO.filter( e => listFilter( e.o ) ).concat( snrInImage );
      list.sort( byPriority );
      this.listItems = list.map( e => ({
         desig: sceDsoDesignation( e.o ),
         alt: sceDsoAltIds( e.o ),
         name: sceDsoName( e.o, this.lang ),
         type: sceTypeName( e.o.type, this.lang ),
         group: sceTypeGroup( e.o.type ),
         mag: e.o.mag
      }) );
      if ( opts.listStars )
         for ( let e of stars )
            if ( e.o.proper && e.o.mag <= 4.5 )
               this.listItems.push( {
                  desig: e.o.bayer,
                  alt: "",
                  name: (this.lang == "ja") ? (e.o.ja || e.o.proper) : e.o.proper,
                  type: sceTypeName( "star", this.lang ) + format( " %.1f", e.o.mag ),
                  group: "star",
                  mag: e.o.mag
               } );
   }

   computeChartFov()
   {
      let info = this.info, opts = this.opts;
      let span = Math.max( info.fovW, info.fovH );
      let fov;
      switch ( opts.chartFovMode )
      {
      case "factor":
         fov = span*opts.chartFovFactor;
         break;
      case "fixed":
         fov = opts.chartFovDeg;
         break;
      default:
         fov = (span > 20) ? span*1.8 : Math.max( 15, Math.min( 50, span*6 ) );
         break;
      }
      return Math.max( Math.max( span*1.1, 0.5 ), Math.min( 150, fov ) );
   }

   /*
    * 星図の恒星: Gaia（XPSD）が使えれば暗い星まで、使えなければ同梱の恒星カタログ
    */
   computeChartStars()
   {
      let fov = this.chartFov, opts = this.opts, info = this.info;
      this.chartGaia = null;
      // 星図の半径が大きいと Gaia の検索に時間がかかる（半径65°で数十秒）ため、半径36°まで
      if ( opts.useGaia && fov*0.75 + 1 <= 36 )
      {
         let lim = (opts.chartStarMag > 0) ? opts.chartStarMag :
                   ((fov >= 60) ? 6.0 : ((fov >= 30) ? 7.0 : ((fov >= 12) ? 8.0 : 9.0)));
         this.chartGaia = this.catalog.gaiaStars( info.ra, info.dec, Math.min( 90, fov*0.75 + 1 ), lim, opts.gaiaDir );
         if ( this.chartGaia )
         {
            this.chartStarLimit = lim;
            return;
         }
      }
      this.chartStarLimit = (opts.chartStarMag > 0) ? opts.chartStarMag :
                            ((fov >= 60) ? 4.8 : ((fov >= 30) ? 5.5 : ((fov >= 12) ? 6.0 : 6.5)));
   }

   autoTitle()
   {
      let info = this.info;
      let best = null, bestScore = Infinity;
      let diag = Math.sqrt( info.width*info.width + info.height*info.height )/2;
      for ( let e of this.allDSO )
      {
         let o = e.o;
         if ( o.rank > 2 )
            continue;
         let dist = Math.sqrt( (e.x - info.width/2)**2 + (e.y - info.height/2)**2 )/diag;
         let size = Math.min( 1, (o.diam || 0)/60/info.scaleDeg/Math.min( info.width, info.height ) );
         let score = o.rank*0.6 + dist*1.5 - size*1.2;
         if ( score < bestScore )
         {
            bestScore = score;
            best = o;
         }
      }
      if ( best )
      {
         let name = sceDsoName( best, this.lang );
         return sceDsoDesignation( best ) + (name ? "  " + name : "");
      }
      return info.object || info.id;
   }

   buildInfoRows()
   {
      let L = this.lang, info = this.info, opts = this.opts;
      let tr = k => sceTr( k, L );
      let rows = [];
      let sys = (info.referenceSystem == "ICRS" || info.referenceSystem == "FK5") ? " (J2000)" : " (" + info.referenceSystem + ")";
      rows.push( [ tr( "rRA" ), sceFormatRA( info.ra ) + sys ] );
      rows.push( [ tr( "rDec" ), sceFormatDec( info.dec ) ] );
      if ( this.constellation )
         rows.push( [ tr( "rConst" ), sceConstellationName( this.constellation, L ) +
                      ((L == "ja") ? "（" + SCE_CONSTELLATIONS[this.constellation][1] + "）" : "") ] );
      rows.push( [ tr( "rFov" ), sceFormatAngle( info.fovW ) + " × " + sceFormatAngle( info.fovH ) ] );
      rows.push( [ tr( "rScale" ), sceFmt( "%.2f″/px", info.scaleArcsec ) ] );

      let focal;
      if ( opts.focalOverride > 0 )
         focal = format( "%.0f mm", opts.focalOverride ) + tr( "focalManual" );
      else if ( info.focalNominal > 0 )
      {
         focal = format( "%.0f mm", info.focalNominal );
         if ( info.focalEffective > 0 && Math.abs( info.focalEffective - info.focalNominal )/info.focalNominal > 0.005 )
            focal += sceFmt( (L == "ja") ? "（%s %.0f mm）" : " (%s %.0f mm)", tr( "focalEffective" ), info.focalEffective );
      }
      else if ( info.focal && info.focal.source == "fov35" )
         focal = tr( "approx" ) + sceFmt( "%.0f mm", info.focal.value ) + tr( "focalFromFov" );
      else if ( info.focal && info.focal.value > 0 )
      {
         let f = info.focal;
         let pxs = ("" + Math.round( f.px*100 )/100);
         focal = format( "%.0f mm", f.value );
         switch ( f.pxSource )
         {
         case "camera":     focal += sceFmt( tr( "focalFromCamera" ), info.camera, pxs ); break;
         case "remembered": focal += sceFmt( tr( "focalFromRemembered" ), pxs ); break;
         case "manual":     focal += sceFmt( tr( "focalFromManualPx" ), pxs ); break;
         default:           focal += sceFmt( tr( "focalFromHeaderPx" ), pxs ); break;
         }
      }
      else
         focal = tr( "focalUnknown" );
      rows.push( [ tr( "rFocal" ), focal ] );

      rows.push( [ tr( "rRotation" ), sceFmt( "%.1f°", info.rotation ) + (info.flipped ? tr( "flipped" ) : "") ] );
      rows.push( [ tr( "rSize" ), sceFmt( "%d × %d px", info.width, info.height ) ] );
      if ( info.dateObs )
         rows.push( [ tr( "rDate" ), sceFormatDateTime( info.dateObs ) ] );
      if ( info.exposure > 0 )
      {
         let e = sceFormatDuration( info.exposure );
         if ( info.nSubs > 1 )
            e += " × " + info.nSubs + " = " + sceFormatDuration( info.exposure*info.nSubs );
         rows.push( [ tr( "rExposure" ), e ] );
      }
      if ( info.telescope ) rows.push( [ tr( "rTelescope" ), info.telescope ] );
      if ( info.camera )    rows.push( [ tr( "rCamera" ), info.camera ] );
      if ( info.filter )    rows.push( [ tr( "rFilter" ), info.filter ] );
      let site = (opts.site || "").trim();
      if ( !site && info.siteLat !== undefined )
         site = sceFmt( "%.4f°%s, %.4f°%s",
                        Math.abs( info.siteLat ), (info.siteLat >= 0) ? "N" : "S",
                        Math.abs( info.siteLon ), (info.siteLon >= 0) ? "E" : "W" );
      if ( site )
         rows.push( [ tr( "rSite" ), site ] );
      let observer = (opts.observer || "").trim() || info.observer;
      if ( observer )
         rows.push( [ tr( "rObserver" ), observer ] );
      return rows;
   }
}

// ----------------------------------------------------------------------------

/*
 * ラベルの重なりを避けて配置する
 */
class SceLabelPlacer
{
   constructor( x0, y0, x1, y1 )
   {
      this.bx0 = x0; this.by0 = y0; this.bx1 = x1; this.by1 = y1;
      this.rects = [];
   }

   fits( x, y, w, h )
   {
      if ( x < this.bx0 || y < this.by0 || x + w > this.bx1 || y + h > this.by1 )
         return false;
      for ( let r of this.rects )
         if ( x < r[2] && x + w > r[0] && y < r[3] && y + h > r[1] )
            return false;
      return true;
   }

   reserve( x, y, w, h )
   {
      this.rects.push( [ x, y, x + w, y + h ] );
   }

   /*
    * アンカー (ax, ay) の周囲（半径 r の外側）に w×h の矩形を置く。左上座標を返す。
    */
   place( w, h, ax, ay, r, pad )
   {
      let d = r + pad, e = (r*0.72) + pad;
      let cands = [
         [ ax + d,          ay - h/2 ],
         [ ax - d - w,      ay - h/2 ],
         [ ax + e,          ay - e - h ],
         [ ax + e,          ay + e ],
         [ ax - w/2,        ay - d - h ],
         [ ax - w/2,        ay + d ],
         [ ax - e - w,      ay - e - h ],
         [ ax - e - w,      ay + e ]
      ];
      // 大きな天体（周囲に置き場が無いもの）は内側の中心付近にも置く
      if ( r > 3*h )
         cands.push( [ ax - w/2, ay - h/2 ], [ ax - w/2, ay - h*1.6 ], [ ax - w/2, ay + h*0.6 ] );
      for ( let c of cands )
         if ( this.fits( c[0], c[1], w, h ) )
         {
            this.reserve( c[0], c[1], w, h );
            return { x: c[0], y: c[1] };
         }
      return null;
   }
}

// ----------------------------------------------------------------------------

class SceRenderer
{
   constructor( model )
   {
      this.model = model;
      this.opts = model.opts;
      this.lang = model.lang;
      this.theme = SCE_THEMES[this.opts.theme] || SCE_THEMES.light;
      this.family = sceResolveFontFamily( this.opts.fontFamily );
      let ps = SCE_PAGE_SIZES[this.opts.pageSize] || SCE_PAGE_SIZES.A4;
      this.W = ps.w;
      this.H = ps.h;
      // 文字や線の太さは用紙の大きさに比例させる
      this.fs = Math.sqrt( (this.W*this.H)/(297*210) );
      this.layout = this.computeLayout();
   }

   tr( key )
   {
      return sceTr( key, this.lang );
   }

   // ------------------------------------------------------------------------
   // 割付（mm）
   // ------------------------------------------------------------------------

   computeLayout()
   {
      let fs = this.fs, W = this.W, H = this.H;
      let m = 8*fs;
      let headerH = 14*fs, footerH = 5*fs, gap = 4*fs;
      let cx0 = m, cx1 = W - m, cy0 = m + headerH, cy1 = H - m - footerH;
      let cw = cx1 - cx0, ch = cy1 - cy0;

      let info = this.model.info;
      let aspect = info.width/info.height;
      let hasMemo = (this.opts.memo || "").trim().length > 0;
      let memoMin = 26*fs;

      // 右列: 星図（上）と天体一覧（下、縦長）
      let w2 = Math.max( 80*fs, Math.min( 110*fs, cw*0.31 ) );
      let col1W = cw - w2 - gap;
      let x2 = cx0 + col1W + gap;
      let photo, infoBox, memo = null, memoInRight = false;

      // 縦長の写真は「写真の右に基本データ」の方が写真を大きく取れる。両方を比べて選ぶ
      let sidePW = Math.min( ch*aspect, col1W - 62*fs - gap );
      let sideArea = (sidePW > 0) ? sidePW*(sidePW/aspect) : 0;
      let belowPH = ch - gap - Math.max( this.layoutInfo( col1W, (col1W >= 150*fs) ? 2 : 1 ).height, hasMemo ? memoMin : 0 );
      let belowPW = Math.min( col1W, belowPH*aspect );
      let belowArea = belowPW*(belowPW/aspect);
      if ( !(sideArea > belowArea*1.1) )
      {
         // 横長の写真: 写真の下に基本データ（とメモ）
         let tryLayout = ( infoW, cols ) =>
         {
            let lay = this.layoutInfo( infoW, cols );
            let ph = ch - gap - Math.max( lay.height, hasMemo ? memoMin : 0 );
            let pw = Math.min( col1W, ph*aspect );
            return { lay: lay, pw: pw, ph: pw/aspect };
         };
         let a;
         if ( hasMemo )
         {
            a = tryLayout( col1W*0.58, 1 );
            if ( a.ph < ch*0.6 && col1W >= 150*fs )
            {
               // 写真が小さくなりすぎるときは、基本データを2段組みにしてメモを右列へ
               a = tryLayout( col1W, 2 );
               memoInRight = true;
            }
         }
         else
            a = tryLayout( col1W, (col1W >= 150*fs) ? 2 : 1 );
         photo = { x: cx0 + (col1W - a.pw)/2, y: cy0, w: a.pw, h: a.ph };
         let ry = cy0 + a.ph + gap, rh = cy1 - ry;
         let infoW = (hasMemo && !memoInRight) ? col1W*0.58 : col1W;
         infoBox = { x: cx0, y: ry, w: infoW, h: rh, lay: a.lay };
         if ( hasMemo && !memoInRight )
            memo = { x: cx0 + infoW + gap, y: ry, w: col1W - infoW - gap, h: rh };
      }
      else
      {
         // 縦長の写真: 写真の右に基本データとメモ
         let pw = sidePW;
         let ph = pw/aspect;
         photo = { x: cx0, y: cy0, w: pw, h: ph };
         let sx = cx0 + pw + gap, sw = col1W - pw - gap;
         let lay = this.layoutInfo( sw, 1 );
         let ih = hasMemo ? Math.min( lay.height, ch - gap - memoMin ) : ch;
         infoBox = { x: sx, y: cy0, w: sw, h: ih, lay: lay };
         if ( hasMemo )
            memo = { x: sx, y: cy0 + ih + gap, w: sw, h: ch - ih - gap };
      }

      let cs = Math.min( w2, ch*0.52 );
      let chart = { x: x2 + (w2 - cs)/2, y: cy0, w: cs, h: cs };
      let listY = cy0 + cs + gap;
      let listH = cy1 - listY;
      if ( memoInRight )
      {
         let mh = Math.max( memoMin, listH*0.32 );
         memo = { x: x2, y: cy1 - mh, w: w2, h: mh };
         listH -= mh + gap;
      }
      let list = { x: x2, y: listY, w: w2, h: listH };

      return {
         margin: m, header: { x: cx0, y: m, w: cw, h: headerH - 2*fs },
         footer: { x: cx0, y: H - m - footerH + 1.5*fs, w: cw, h: footerH - 1.5*fs },
         photo: photo, list: list, chart: chart, info: infoBox, memo: memo, gap: gap
      };
   }

   /*
    * 割付計算用のフォント（300 dpi 相当）。幅は mm に換算して使う。
    */
   mfont( pt, bold = false )
   {
      let f = this.family ? new Font( this.family, 12 ) : new Font( FontFamily.SansSerif, 12 );
      f.pixelSize = Math.max( 1, Math.round( pt*this.fs*300/72 ) );
      if ( bold )
         f.bold = true;
      return f;
   }

   /*
    * 基本データの段組み（値は省略せずに折り返す）。寸法は mm。
    */
   layoutInfo( widthMM, cols )
   {
      let fs = this.fs, k = 300/25.4;
      let f = this.mfont( 6.8 );
      let colGap = 5*fs;
      let colW = (widthMM - (cols - 1)*colGap)/cols;
      let labelW = 0;
      for ( let r of this.model.infoRows )
         labelW = Math.max( labelW, f.width( r[0] )/k );
      labelW = Math.min( labelW, colW*0.38 ) + 2.5*fs;
      let valueW = colW - labelW;
      let items = this.model.infoRows.map( r => ({ label: r[0], lines: this.wrap( f, r[1], valueW*k ) }) );
      let total = 0;
      for ( let it of items )
         total += it.lines.length;
      let per = Math.ceil( total/cols );
      let columns = [];
      let cur = [], n = 0;
      for ( let it of items )
      {
         if ( n > 0 && n + it.lines.length > per && columns.length < cols - 1 )
         {
            columns.push( cur );
            cur = [];
            n = 0;
         }
         cur.push( it );
         n += it.lines.length;
      }
      columns.push( cur );
      let maxLines = 0;
      for ( let c of columns )
      {
         let s = 0;
         for ( let it of c )
            s += it.lines.length;
         maxLines = Math.max( maxLines, s );
      }
      return {
         columns: columns, colW: colW, colGap: colGap, labelW: labelW,
         height: this.sectionTitleMM() + maxLines*this.infoRowMM() + 1.5*fs
      };
   }

   // 見出しの高さと基本データ1行の高さ（mm）。描画時も同じ値を使う。
   sectionTitleMM()
   {
      return (8.2*0.3528*1.45 + 2.8)*this.fs;
   }

   infoRowMM()
   {
      return 6.8*0.3528*1.42*this.fs;
   }

   // 写真の印刷サイズ（mm）
   photoSizeMM()
   {
      return { w: this.layout.photo.w, h: this.layout.photo.h };
   }

   // ------------------------------------------------------------------------
   // 描画の基本
   // ------------------------------------------------------------------------

   px( mm )
   {
      return mm*this.k;
   }

   font( pt, bold = false )
   {
      let f = this.family ? new Font( this.family, 12 ) : new Font( FontFamily.SansSerif, 12 );
      f.pixelSize = Math.max( 1, Math.round( pt*this.fs*this.dpi/72 ) );
      if ( bold )
         f.bold = true;
      return f;
   }

   pen( rgb, widthMM, alpha = 255, style = PenStyle.Solid )
   {
      return new Pen( sceARGB( rgb, alpha ), Math.max( 0.5, this.px( widthMM*this.fs ) ), style, PenCap.Round, PenJoin.Round );
   }

   brush( rgb, alpha = 255 )
   {
      return new Brush( sceARGB( rgb, alpha ) );
   }

   text( g, font, rgb, x, y, s, alpha = 255 )
   {
      g.font = font;
      g.pen = new Pen( sceARGB( rgb, alpha ) );
      g.drawText( x, y, s );
   }

   // 幅に収まるよう末尾を省略
   fit( font, s, maxW )
   {
      if ( font.width( s ) <= maxW )
         return s;
      let lo = 0, hi = s.length;
      while ( lo < hi )
      {
         let mid = (lo + hi + 1) >> 1;
         if ( font.width( s.substring( 0, mid ) + "…" ) <= maxW )
            lo = mid;
         else
            hi = mid - 1;
      }
      return (lo > 0) ? s.substring( 0, lo ) + "…" : "";
   }

   // 折り返し（英文は単語単位、日本語は文字単位。行頭禁則の簡易処理つき）
   wrap( font, text, maxW )
   {
      let lines = [];
      let noStart = "、。，．,.)）」』】〕〉》！？!?ー・：；";
      for ( let para of text.split( /\r?\n/ ) )
      {
         if ( para.length == 0 )
         {
            lines.push( "" );
            continue;
         }
         let cur = "";
         for ( let i = 0; i < para.length; ++i )
         {
            let ch = para[i];
            let next = cur + ch;
            if ( cur.length > 0 && font.width( next ) > maxW )
            {
               if ( noStart.indexOf( ch ) >= 0 )
               {
                  // 句読点は前の行にぶら下げる
                  lines.push( next );
                  cur = "";
                  continue;
               }
               let sp = cur.lastIndexOf( " " );
               if ( ch != " " && sp > 0 && /[\x21-\x7e]/.test( ch ) && cur.length - sp < 24 )
               {
                  lines.push( cur.substring( 0, sp ) );
                  cur = cur.substring( sp + 1 ) + ch;
               }
               else
               {
                  lines.push( cur );
                  cur = (ch == " ") ? "" : ch;
               }
            }
            else
               cur = next;
         }
         lines.push( cur );
      }
      return lines;
   }

   rectPx( r )
   {
      return new Rect( this.px( r.x ), this.px( r.y ), this.px( r.x + r.w ), this.px( r.y + r.h ) );
   }

   sectionTitle( g, x, y, w, title )
   {
      let f = this.font( 8.2, true );
      let base = this.px( y ) + f.ascent;
      this.text( g, f, this.theme.accent, this.px( x ), base, title );
      let ly = base + this.px( 1.2*this.fs );
      g.pen = this.pen( this.theme.line, 0.25 );
      g.drawLine( this.px( x ), ly, this.px( x + w ), ly );
      return ly + this.px( 1.6*this.fs );   // 本文の開始 y (px)
   }

   // ------------------------------------------------------------------------
   // ページ全体
   // ------------------------------------------------------------------------

   render( g, dpi, photoBmp )
   {
      this.dpi = dpi;
      this.k = dpi/25.4;
      g.antialiasing = true;
      g.textAntialiasing = true;
      g.smoothInterpolation = true;

      g.fillRect( 0, 0, this.px( this.W ), this.px( this.H ), this.brush( this.theme.page ) );

      this.drawHeader( g );
      this.drawPhoto( g, photoBmp );
      this.drawChart( g );
      this.drawInfo( g );
      this.drawList( g );
      if ( this.layout.memo )
         this.drawMemo( g );
      this.drawFooter( g );
   }

   drawHeader( g )
   {
      let L = this.layout.header, t = this.theme, info = this.model.info;
      let fT = this.font( 15, true );
      let fS = this.font( 7.6 );
      let x = this.px( L.x ), y = this.px( L.y );

      // 右側: 撮影日・撮影者
      let rightLines = [];
      if ( info.dateObs )
         rightLines.push( sceTr( "rDate", this.lang ) + "  " + sceFormatDateTime( info.dateObs ) );
      let observer = (this.opts.observer || "").trim() || info.observer;
      let site = (this.opts.site || "").trim();
      let who = [ observer, site ].filter( s => s ).join( "  ·  " );
      if ( who )
         rightLines.push( who );
      let rw = 0;
      for ( let s of rightLines )
         rw = Math.max( rw, fS.width( s ) );
      let xr = this.px( L.x + L.w );
      for ( let i = 0; i < rightLines.length; ++i )
      {
         let s = rightLines[i];
         this.text( g, fS, t.sub, xr - fS.width( s ), y + fS.ascent + i*fS.lineSpacing*1.15, s );
      }

      // 左側: タイトルと副題
      let maxTitleW = this.px( L.w ) - rw - this.px( 6*this.fs );
      this.text( g, fT, t.text, x, y + fT.ascent, this.fit( fT, this.model.title, maxTitleW ) );
      let parts = [];
      if ( this.model.constellation )
         parts.push( sceConstellationName( this.model.constellation, this.lang ) );
      parts.push( "RA " + sceFormatRA( info.ra, 0 ).replace( /\s/g, "" ) + "  Dec " + sceFormatDec( info.dec ).replace( /\s/g, "" ) );
      parts.push( sceTr( "rFov", this.lang ) + " " + sceFormatAngle( info.fovW ) + " × " + sceFormatAngle( info.fovH ) );
      let sub = parts.join( "   ·   " );
      this.text( g, fS, t.sub, x, y + fT.ascent + fT.descent + fS.ascent + this.px( 0.6*this.fs ), this.fit( fS, sub, maxTitleW ) );

      let ly = this.px( L.y + L.h + 0.6*this.fs );
      g.pen = this.pen( t.line, 0.35 );
      g.drawLine( x, ly, this.px( L.x + L.w ), ly );
   }

   drawFooter( g )
   {
      let L = this.layout.footer, t = this.theme;
      let f = this.font( 6 );
      let y = this.px( L.y ) + f.ascent;
      let now = new Date;
      let made = sceTr( "footerMade", this.lang ) + " " + now.getFullYear() + "-" + scePad( now.getMonth() + 1, 2 ) + "-" + scePad( now.getDate(), 2 );
      let left = "SkyChartExporter " + SCE_VERSION + "  ·  PixInsight " +
                 CoreApplication.versionMajor + "." + CoreApplication.versionMinor + "." + CoreApplication.versionRelease +
                 "  ·  " + made +
                 ((this.model.catalog.source == "stellarium") ? "  ·  " + this.tr( "catCredit" ) : "") +
                 (this.model.chartGaia ? "  ·  Gaia DR3 (ESA)" : "");
      this.text( g, f, t.sub, this.px( L.x ), y, left );
      let info = this.model.info;
      let right = info.filePath ? File.extractName( info.filePath ) + File.extractExtension( info.filePath ) : info.id;
      right = this.fit( f, right, this.px( L.w*0.45 ) );
      this.text( g, f, t.sub, this.px( L.x + L.w ) - f.width( right ), y, right );
   }

   // ------------------------------------------------------------------------
   // 写真とアノテーション
   // ------------------------------------------------------------------------

   drawPhoto( g, bmp )
   {
      let R = this.layout.photo;
      let r = this.rectPx( R );
      g.drawScaledBitmap( r, bmp );

      let info = this.model.info;
      // 画像座標 → ページ座標(px)
      let sx = r.width/info.width;
      this.photoMap = ( x, y ) => new Point( r.x0 + x*sx, r.y0 + y*sx );
      this.photoScale = sx;   // ページpx / 画像px
      this.photoRect = r;

      if ( sceAnnotationsEnabled( this.opts ) )
      {
         g.pushState();
         g.clipRect = r;
         g.clipping = true;
         try
         {
            this.drawAnnotations( g );
         }
         finally
         {
            g.popState();
         }
      }

      g.pen = this.pen( this.theme.line, 0.3 );
      g.drawRect( r.x0, r.y0, r.x1, r.y1 );
   }

   /*
    * 天球上の折れ線の配列 → ページ上の折れ線の配列（無効な点で分割）
    */
   skyPolylinesToPhoto( polylines )
   {
      let info = this.model.info;
      let flat = [];
      let index = [];
      let lim = info.radius*1.6 + 2;
      for ( let li = 0; li < polylines.length; ++li )
         for ( let pi = 0; pi < polylines[li].length; ++pi )
         {
            let p = polylines[li][pi];
            if ( sceAngDist( info.ra, info.dec, p.ra, p.dec ) <= lim )
            {
               index.push( [ li, pi ] );
               flat.push( p.ra, p.dec );
            }
         }
      if ( flat.length == 0 )
         return [];
      let xy = info.window.celestialToImage( new Float64Array( flat ) );
      let back = info.window.imageToCelestial( xy );
      let tol = Math.max( 0.02, 30*info.scaleDeg );
      let valid = polylines.map( l => new Array( l.length ).fill( null ) );
      for ( let i = 0; i < index.length; ++i )
      {
         let x = xy[2*i], y = xy[2*i + 1];
         if ( !isFinite( x ) || !isFinite( y ) || !isFinite( back[2*i] ) )
            continue;
         if ( sceAngDist( back[2*i], back[2*i + 1], flat[2*i], flat[2*i + 1] ) > tol )
            continue;
         valid[index[i][0]][index[i][1]] = this.photoMap( x, y );
      }
      let out = [];
      let maxJump = this.px( Math.max( this.layout.photo.w, this.layout.photo.h ) );
      for ( let l of valid )
      {
         let cur = [];
         for ( let p of l )
         {
            if ( p === null || (cur.length > 0 && Math.abs( p.x - cur[cur.length - 1].x ) + Math.abs( p.y - cur[cur.length - 1].y ) > maxJump) )
            {
               if ( cur.length > 1 )
                  out.push( cur );
               cur = (p === null) ? [] : [ p ];
            }
            else
               cur.push( p );
         }
         if ( cur.length > 1 )
            out.push( cur );
      }
      return out;
   }

   drawAnnotations( g )
   {
      let opts = this.opts, info = this.model.info, C = SCE_PHOTO_COLORS;
      let r = this.photoRect;
      let placer = new SceLabelPlacer( r.x0 + 1, r.y0 + 1, r.x1 - 1, r.y1 - 1 );
      let labelFont = this.font( opts.aLabelSize );
      let labelFontB = this.font( opts.aLabelSize, true );
      let shadow = Math.max( 1, this.px( 0.12*this.fs ) );

      // --- 座標グリッド ---
      if ( opts.aGrid )
         this.drawPhotoGrid( g );

      // --- 星座境界・星座線 ---
      if ( opts.aConstBorders )
      {
         let lines = [];
         for ( let b of this.model.catalog.constBorders )
            if ( b.pts.some( p => sceAngDist( info.ra, info.dec, p.ra, p.dec ) < info.radius + 15 ) )
               lines.push( this.densify( b.pts, Math.max( 0.05, info.radius/15 ) ) );
         g.pen = this.pen( C.border, 0.25, 170, PenStyle.Dash );
         for ( let pl of this.skyPolylinesToPhoto( lines ) )
            g.drawPolyline( pl );
      }
      if ( opts.aConstLines )
      {
         let lines = [];
         for ( let l of this.model.catalog.constLines )
            if ( l.pts.some( p => sceAngDist( info.ra, info.dec, p.ra, p.dec ) < info.radius + 30 ) )
               lines.push( this.densify( l.pts, Math.max( 0.05, info.radius/15 ) ) );
         g.pen = this.pen( C.constLine, 0.3, 190 );
         for ( let pl of this.skyPolylinesToPhoto( lines ) )
            g.drawPolyline( pl );
      }

      let labels = [];

      // --- 星雲・星団・銀河 ---
      for ( let e of this.model.annotDSO )
      {
         let o = e.o;
         let color = C[sceTypeGroup( o.type )] || C.other;
         let lineAlpha = (o.rank <= 2) ? 235 : 160;
         let lineW = (o.rank <= 2) ? 0.3 : 0.22;
         let p = this.photoMap( e.x, e.y );
         let semi = (o.diam || 0)/120/info.scaleDeg*this.photoScale;   // 長半径（ページpx）
         let rr;
         if ( semi >= this.px( 1.8*this.fs ) )
         {
            let poly = this.skyEllipse( o );
            if ( poly && poly.length > 2 )
            {
               g.strokePolygon( poly, this.pen( color, lineW, lineAlpha ) );
               rr = semi*((o.axisRatio > 1) ? 1/o.axisRatio : 1);
               rr = Math.max( rr, this.px( 1.2*this.fs ) );
            }
            else
               rr = this.px( 1.6*this.fs );
         }
         else
         {
            rr = this.px( 1.6*this.fs );
            g.strokeCircle( p.x, p.y, rr, this.pen( color, lineW, lineAlpha ) );
         }
         let name = sceDsoName( o, this.lang );
         let s = sceDsoDesignation( o ) + ((opts.aLabelNames && name) ? " " + name : "");
         // 中心が画像外の大きな天体は、ラベルの基点を画像内に寄せる
         let ax = Math.max( r.x0 + this.px( 3 ), Math.min( r.x1 - this.px( 3 ), p.x ) );
         let ay = Math.max( r.y0 + this.px( 3 ), Math.min( r.y1 - this.px( 3 ), p.y ) );
         if ( ax != p.x || ay != p.y )
            rr = 0;
         labels.push( { s: s, color: color, ax: ax, ay: ay, r: rr, bold: o.rank <= 1 } );
         // 小さな印の上にほかのラベルが重ならないようにする（大きな天体は対象外）
         if ( rr <= this.px( 3*this.fs ) )
            placer.reserve( p.x - rr, p.y - rr, rr*2, rr*2 );
      }

      // --- 恒星 ---
      for ( let e of this.model.annotStars )
      {
         let o = e.o;
         let p = this.photoMap( e.x, e.y );
         let rr = this.px( (1.3 + Math.max( 0, 3 - o.mag )*0.25)*this.fs );
         g.strokeCircle( p.x, p.y, rr, this.pen( C.star, 0.25, 210 ) );
         let s = (this.lang == "ja") ? (o.ja || o.proper || o.bayer) : (o.proper || o.bayer);
         if ( o.proper && opts.aLabelNames && s != o.bayer )
            s = o.bayer + " " + s;
         labels.push( { s: s, color: C.star, ax: p.x, ay: p.y, r: rr, bold: false } );
      }

      // --- 星座名 ---
      if ( opts.aConstNames )
      {
         let fC = this.font( opts.aLabelSize*1.3, true );
         for ( let c in this.model.catalog.constCenters )
         {
            let cc = this.model.catalog.constCenters[c];
            if ( sceAngDist( info.ra, info.dec, cc.ra, cc.dec ) > info.radius*1.2 )
               continue;
            let xy = info.window.celestialToImage( cc.ra, cc.dec );
            if ( !xy || xy.x < 0 || xy.y < 0 || xy.x >= info.width || xy.y >= info.height )
               continue;
            let p = this.photoMap( xy.x, xy.y );
            let s = sceConstellationName( c, this.lang );
            let w = fC.width( s ), h = fC.height;
            if ( placer.fits( p.x - w/2, p.y - h/2, w, h ) )
            {
               placer.reserve( p.x - w/2, p.y - h/2, w, h );
               this.text( g, fC, C.constName, p.x - w/2, p.y - h/2 + fC.ascent, s, 200 );
            }
         }
      }

      // --- ラベル ---
      let pad = this.px( 0.7*this.fs );
      for ( let lb of labels )
      {
         let f = lb.bold ? labelFontB : labelFont;
         let w = f.width( lb.s ), h = f.height;
         let pos = placer.place( w, h, lb.ax, lb.ay, lb.r, pad );
         if ( !pos )
            continue;
         let base = pos.y + f.ascent;
         this.text( g, f, 0x000000, pos.x + shadow, base + shadow, lb.s, 200 );
         this.text( g, f, lb.color, pos.x, base, lb.s );
      }

      if ( opts.aCompass )
         this.drawCompass( g );
   }

   densify( pts, step )
   {
      let out = [];
      for ( let i = 0; i + 1 < pts.length; ++i )
      {
         let seg = sceGreatCircleSamples( pts[i].ra, pts[i].dec, pts[i + 1].ra, pts[i + 1].dec, step );
         if ( i > 0 )
            seg.shift();
         for ( let p of seg )
            out.push( p );
      }
      return out;
   }

   /*
    * 天体の楕円（長径・軸比・位置角）を天球上で作り、ページ座標の多角形にする
    */
   skyEllipse( o )
   {
      let info = this.model.info;
      let a = (o.diam || 0)/120;    // 長半径（度）
      let q = (o.axisRatio > 1) ? 1/o.axisRatio : 1;
      let b = a*q;
      let pa = (o.pa !== undefined) ? o.pa : 0;
      let n = 48;
      let flat = new Float64Array( 2*n );
      for ( let i = 0; i < n; ++i )
      {
         let t = 2*Math.PI*i/n;
         let ct = Math.cos( t ), st = Math.sin( t );
         let rad = (a*b)/Math.sqrt( (b*ct)*(b*ct) + (a*st)*(a*st) );
         let p = sceOffsetPoint( o.ra, o.dec, pa + t*SCE_R2D, rad );
         flat[2*i] = p.ra;
         flat[2*i + 1] = p.dec;
      }
      let xy = info.window.celestialToImage( flat );
      let poly = [];
      for ( let i = 0; i < n; ++i )
         if ( isFinite( xy[2*i] ) && isFinite( xy[2*i + 1] ) )
            poly.push( this.photoMap( xy[2*i], xy[2*i + 1] ) );
      return poly;
   }

   drawPhotoGrid( g )
   {
      let info = this.model.info, C = SCE_PHOTO_COLORS, r = this.photoRect;
      let span = Math.max( info.fovW, info.fovH );
      let decStep = sceNiceStep( span, 5, SCE_DEC_STEPS );
      let poleIn = (90 - Math.abs( info.dec )) < info.radius*1.05;
      let decMin = 90, decMax = -90;
      for ( let p of info.boundsRD )
      {
         decMin = Math.min( decMin, p.dec );
         decMax = Math.max( decMax, p.dec );
      }
      let raMin, raMax;
      if ( poleIn )
      {
         raMin = 0;
         raMax = 360;
         if ( info.dec > 0 ) decMax = 90; else decMin = -90;
      }
      else
      {
         let lo = 0, hi = 0;
         for ( let p of info.boundsRD )
         {
            let d = ((p.ra - info.ra + 540) % 360) - 180;
            lo = Math.min( lo, d );
            hi = Math.max( hi, d );
         }
         raMin = info.ra + lo;
         raMax = info.ra + hi;
      }
      // 赤経の線は高緯度ほど密になるので、本数の目安を cosδ で増やす
      let raStep = sceNiceStep( raMax - raMin, Math.max( 3, 5/Math.max( 0.25, Math.cos( info.dec*SCE_D2R ) ) ), SCE_RA_STEPS );
      let lines = [], kinds = [], values = [];
      let n = 64;
      for ( let d = Math.ceil( (decMin - decStep)/decStep )*decStep; d <= decMax + decStep; d += decStep )
      {
         if ( Math.abs( d ) >= 89.999 )
            continue;
         let pts = [];
         let a0 = raMin - raStep, a1 = raMax + raStep;
         for ( let i = 0; i <= n; ++i )
            pts.push( { ra: sceNormRA( a0 + (a1 - a0)*i/n ), dec: d } );
         lines.push( pts );
         kinds.push( "dec" );
         values.push( d );
      }
      for ( let a = Math.ceil( (raMin - raStep)/raStep )*raStep; a <= raMax + raStep; a += raStep )
      {
         if ( poleIn && a >= 360 )
            break;
         let pts = [];
         let d0 = Math.max( -90, decMin - decStep ), d1 = Math.min( 90, decMax + decStep );
         for ( let i = 0; i <= n; ++i )
            pts.push( { ra: sceNormRA( a ), dec: d0 + (d1 - d0)*i/n } );
         lines.push( pts );
         kinds.push( "ra" );
         values.push( sceNormRA( a ) );
      }

      let f = this.font( Math.max( 4.5, this.opts.aLabelSize*0.8 ) );
      let off = this.px( 0.8*this.fs );
      g.pen = this.pen( C.grid, 0.18, 85 );
      for ( let i = 0; i < lines.length; ++i )
      {
         let pls = this.skyPolylinesToPhoto( [ lines[i] ] );
         g.pen = this.pen( C.grid, 0.18, 85 );
         for ( let pl of pls )
            g.drawPolyline( pl );
         // ラベル: 左端または下端との交点
         let label = (kinds[i] == "dec") ? sceFormatDecShort( values[i] ) : sceFormatRAShort( values[i] );
         let done = false;
         for ( let pl of pls )
         {
            for ( let j = 0; j + 1 < pl.length && !done; ++j )
            {
               let p = pl[j], q = pl[j + 1];
               if ( (p.x - r.x0)*(q.x - r.x0) < 0 )
               {
                  let t = (r.x0 - p.x)/(q.x - p.x);
                  let y = p.y + t*(q.y - p.y);
                  if ( y > r.y0 + f.height && y < r.y1 - f.height )
                  {
                     this.text( g, f, C.grid, r.x0 + off, y - off, label, 170 );
                     done = true;
                  }
               }
               else if ( (p.y - r.y1)*(q.y - r.y1) < 0 )
               {
                  let t = (r.y1 - p.y)/(q.y - p.y);
                  let x = p.x + t*(q.x - p.x);
                  if ( x > r.x0 + this.px( 12 ) && x < r.x1 - f.width( label ) - off )
                  {
                     this.text( g, f, C.grid, x + off, r.y1 - off, label, 170 );
                     done = true;
                  }
               }
            }
            if ( done )
               break;
         }
      }
   }

   drawCompass( g )
   {
      let info = this.model.info, C = SCE_PHOTO_COLORS, r = this.photoRect;
      let cx = info.width/2, cy = info.height/2;
      let d = 0.05*Math.min( info.fovW, info.fovH );
      let pn = info.window.celestialToImage( info.ra, Math.min( 89.9, info.dec + d ) );
      let pe = info.window.celestialToImage( info.ra + d/Math.max( 0.05, Math.cos( info.dec*SCE_D2R ) ), info.dec );
      let L = this.px( 6*this.fs );
      let ox = r.x0 + this.px( 9*this.fs ), oy = r.y1 - this.px( 9*this.fs );
      let f = this.font( 6.5, true );
      let pen = this.pen( C.compass, 0.35, 230 );
      let shadowPen = this.pen( 0x000000, 0.6, 140 );
      let arrow = ( p, label ) =>
      {
         if ( !p )
            return;
         let vx = p.x - cx, vy = p.y - cy;
         let n = Math.sqrt( vx*vx + vy*vy );
         if ( !(n > 0) )
            return;
         vx /= n; vy /= n;
         let ex = ox + vx*L, ey = oy + vy*L;
         let hx = -vy, hy = vx, ah = L*0.22;
         for ( let pn of [ shadowPen, pen ] )
         {
            g.pen = pn;
            g.drawLine( ox, oy, ex, ey );
            g.drawLine( ex, ey, ex - vx*ah + hx*ah*0.5, ey - vy*ah + hy*ah*0.5 );
            g.drawLine( ex, ey, ex - vx*ah - hx*ah*0.5, ey - vy*ah - hy*ah*0.5 );
         }
         let tx = ox + vx*(L + f.height*0.7) - f.width( label )/2;
         let ty = oy + vy*(L + f.height*0.7) + f.ascent/2 - f.descent/2;
         this.text( g, f, 0x000000, tx + 1, ty + 1, label, 160 );
         this.text( g, f, C.compass, tx, ty, label );
      };
      arrow( pn, "N" );
      arrow( pe, "E" );

      // スケールバー
      let target = 0.2*info.fovW;
      let cands = [ 1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600, 7200, 18000, 36000, 72000 ]; // 秒角
      let L2 = cands[0];
      for ( let c of cands )
         if ( c/3600 <= target )
            L2 = c;
      let lenPx = (L2/3600)/info.scaleDeg*this.photoScale;
      let bx1 = r.x1 - this.px( 6*this.fs ), bx0 = bx1 - lenPx, by = r.y1 - this.px( 6*this.fs );
      let label = sceFormatAngle( L2/3600 );
      for ( let pn of [ shadowPen, pen ] )
      {
         g.pen = pn;
         g.drawLine( bx0, by, bx1, by );
         g.drawLine( bx0, by - this.px( 0.9 ), bx0, by + this.px( 0.9 ) );
         g.drawLine( bx1, by - this.px( 0.9 ), bx1, by + this.px( 0.9 ) );
      }
      let tx = (bx0 + bx1)/2 - f.width( label )/2, ty = by - this.px( 1.4*this.fs );
      this.text( g, f, 0x000000, tx + 1, ty + 1, label, 160 );
      this.text( g, f, C.compass, tx, ty, label );
   }

   // ------------------------------------------------------------------------
   // 星図
   // ------------------------------------------------------------------------

   drawChart( g )
   {
      let R = this.layout.chart, t = this.theme, opts = this.opts, info = this.model.info;
      let cat = this.model.catalog;
      let rect = this.rectPx( R );
      let fov = this.model.chartFov;
      let proj = new SceStereoProjection( info.ra, info.dec );
      let half = rect.width/2;
      let kk = half/SceStereoProjection.radiusForAngle( fov/2 );
      let cx = (rect.x0 + rect.x1)/2, cy = (rect.y0 + rect.y1)/2;
      let lim = fov*0.75 + 2;
      let xy = ( ra, dec ) =>
      {
         let p = proj.project( ra, dec );
         return p ? new Point( cx - p.x*kk, cy - p.y*kk ) : null;
      };
      let inside = ( p, m = 0 ) => p && p.x >= rect.x0 - m && p.x <= rect.x1 + m && p.y >= rect.y0 - m && p.y <= rect.y1 + m;
      let cs = rect.width/this.px( 95*this.fs );   // 星図の大きさによる倍率

      // 図のタイトル
      let fTitle = this.font( 8.2, true );
      // 背景
      g.fillRect( rect, this.brush( t.chartBg ) );

      g.pushState();
      g.clipRect = rect;
      g.clipping = true;
      try
      {
         let drawSkyLine = ( pts, pen ) =>
         {
            let cur = [];
            g.pen = pen;
            for ( let p of pts )
            {
               let q = (sceAngDist( info.ra, info.dec, p.ra, p.dec ) < lim + 10) ? xy( p.ra, p.dec ) : null;
               if ( q === null )
               {
                  if ( cur.length > 1 )
                     g.drawPolyline( cur );
                  cur = [];
               }
               else
                  cur.push( q );
            }
            if ( cur.length > 1 )
               g.drawPolyline( cur );
         };
         let step = Math.max( 0.1, fov/60 );

         // --- グリッド ---
         let gridLabels = [];
         if ( opts.chartGrid )
         {
            let decStep = sceNiceStep( fov, 6, SCE_DEC_STEPS );
            let cosd = Math.max( 0.1, Math.cos( info.dec*SCE_D2R ) );
            let raStep = sceNiceStep( fov/cosd, 6, SCE_RA_STEPS );
            let poleIn = (90 - Math.abs( info.dec )) < fov*0.75;
            let d0 = Math.max( -90, info.dec - lim ), d1 = Math.min( 90, info.dec + lim );
            let raSpan = poleIn ? 180 : Math.min( 180, lim/cosd );
            let pen = this.pen( t.chartGrid, 0.18 );
            for ( let d = Math.ceil( d0/decStep )*decStep; d <= d1; d += decStep )
            {
               if ( Math.abs( d ) >= 89.999 )
                  continue;
               let pts = [];
               let n = Math.ceil( 2*raSpan*Math.max( 0.05, Math.cos( d*SCE_D2R ) )/step ) + 2;
               for ( let i = 0; i <= n; ++i )
                  pts.push( { ra: info.ra - raSpan + 2*raSpan*i/n, dec: d } );
               drawSkyLine( pts, pen );
               gridLabels.push( { kind: "dec", v: d, pts: pts } );
            }
            let a0 = info.ra - raSpan, a1 = info.ra + raSpan;
            for ( let a = Math.ceil( a0/raStep )*raStep; a < a1 + 1e-9; a += raStep )
            {
               if ( poleIn && a >= a0 + 360 - 1e-9 )
                  break;
               let pts = [];
               let n = Math.ceil( (d1 - d0)/step ) + 2;
               for ( let i = 0; i <= n; ++i )
                  pts.push( { ra: a, dec: d0 + (d1 - d0)*i/n } );
               drawSkyLine( pts, pen );
               gridLabels.push( { kind: "ra", v: sceNormRA( a ), pts: pts } );
            }
         }

         // --- 星座境界 ---
         if ( opts.chartConstBorders )
         {
            let pen = this.pen( t.chartBorder, 0.22, 255, PenStyle.Dash );
            for ( let b of cat.constBorders )
               if ( b.pts.some( p => sceAngDist( info.ra, info.dec, p.ra, p.dec ) < lim + 20 ) )
                  drawSkyLine( this.densify( b.pts, step ), pen );
         }

         // --- 星座線 ---
         if ( opts.chartConstLines )
         {
            let pen = this.pen( t.chartConst, 0.32, 210 );
            for ( let l of cat.constLines )
               if ( l.pts.some( p => sceAngDist( info.ra, info.dec, p.ra, p.dec ) < lim + 30 ) )
                  drawSkyLine( this.densify( l.pts, step ), pen );
         }

         let placer = new SceLabelPlacer( rect.x0 + 2, rect.y0 + 2, rect.x1 - 2, rect.y1 - 2 );
         // 左上の方位記号と右上の注記
         placer.reserve( rect.x0, rect.y0, this.px( 12*this.fs ), this.px( 10*this.fs ) );
         placer.reserve( rect.x1 - this.px( 42*this.fs ), rect.y0, this.px( 42*this.fs ), this.px( 4.5*this.fs ) );

         // --- 撮影範囲（先に予約して、ラベルが重ならないようにする） ---
         let frame = [];
         for ( let p of info.boundsRD )
         {
            let q = xy( p.ra, p.dec );
            if ( q )
               frame.push( q );
         }
         let fx0 = Infinity, fy0 = Infinity, fx1 = -Infinity, fy1 = -Infinity;
         for ( let p of frame )
         {
            fx0 = Math.min( fx0, p.x ); fy0 = Math.min( fy0, p.y );
            fx1 = Math.max( fx1, p.x ); fy1 = Math.max( fy1, p.y );
         }
         if ( frame.length > 2 )
            placer.reserve( fx0, fy0, fx1 - fx0, fy1 - fy0 );

         // --- 恒星 ---
         // Gaia があれば明るい星（〜3.5等）だけ同梱カタログで描き、それより暗い星は Gaia で描く
         let starLim = this.model.chartStarLimit;
         this.chartStarLimit = starLim;
         let gaia = this.model.chartGaia;
         let brightLim = gaia ? Math.min( starLim, 3.5 ) : starLim;
         let starBrush = this.brush( t.chartStar );
         let bgPen = new Pen( sceARGB( t.chartBg ), Math.max( 0.5, this.px( 0.12*this.fs ) ) );
         let sizeK = Math.min( 1.25, 6/starLim );
         let starRadius = mag => Math.min( this.px( (0.2 + 0.3*Math.max( 0, starLim - mag )*sizeK)*this.fs*Math.min( 1.4, cs ) ),
                                           this.px( 1.9*this.fs*cs ) );
         let starLabels = [];
         let drawnBright = [];
         let labelLim = (fov < 15) ? 4.0 : Math.min( 2.6, starLim - 1.5 );
         g.pen = bgPen;
         g.brush = starBrush;
         for ( let s of cat.stars )
         {
            if ( s.mag > Math.max( brightLim, labelLim ) )
               break;
            if ( Math.abs( s.dec - info.dec ) > lim )
               continue;
            if ( sceAngDist( info.ra, info.dec, s.ra, s.dec ) > lim )
               continue;
            let p = xy( s.ra, s.dec );
            if ( !inside( p, 5 ) )
               continue;
            let rad = starRadius( s.mag );
            if ( s.mag <= brightLim )
            {
               g.drawCircle( p.x, p.y, rad );
               drawnBright.push( s );
            }
            if ( s.mag <= Math.min( 2.6, starLim - 1.5 ) || (fov < 15 && s.proper && s.mag <= 4.0) )
               starLabels.push( { s: s, p: p, r: rad } );
         }
         if ( gaia )
         {
            for ( let s of gaia )
            {
               if ( !(s.mag > 3.0) || s.mag > starLim )
                  continue;
               let p = xy( s.ra, s.dec );
               if ( !inside( p, 3 ) )
                  continue;
               // 同梱カタログで描いた明るい星との重複を除く
               if ( s.mag < brightLim + 0.7 &&
                    drawnBright.some( b => Math.abs( b.dec - s.dec ) < 0.05 && sceAngDist( b.ra, b.dec, s.ra, s.dec ) < 0.05 ) )
                  continue;
               g.drawCircle( p.x, p.y, starRadius( s.mag ) );
            }
         }

         // --- 星雲・星団・銀河 ---
         let dsoLabels = [];
         if ( opts.chartDSO )
         {
            let dsoMag = (fov >= 45) ? 6 : ((fov >= 25) ? 8 : ((fov >= 10) ? 10 : 12));
            for ( let o of cat.dso )
            {
               let named = (o.rank == 0) || (o.rank <= 1 && fov < 60) || ((o.ja || o.en) && fov < 25);
               if ( !named && !(o.mag !== undefined && o.mag <= dsoMag) )
                  continue;
               if ( Math.abs( o.dec - info.dec ) > lim || sceAngDist( info.ra, info.dec, o.ra, o.dec ) > lim )
                  continue;
               let p = xy( o.ra, o.dec );
               if ( !inside( p, 3 ) )
                  continue;
               let grp = sceTypeGroup( o.type );
               let color = t.dso[grp] || t.dso.other;
               let semi = (o.diam || 0)/120/(fov/2)*half;
               let rad = Math.max( this.px( 0.9*this.fs*Math.min( 1.3, cs ) ), Math.min( semi, half*0.6 ) );
               this.chartSymbol( g, grp, p, rad, o, color, xy );
               dsoLabels.push( { o: o, p: p, r: rad, color: color } );
            }
         }

         // --- 撮影範囲の枠 ---
         if ( frame.length > 2 )
         {
            let small = Math.max( fx1 - fx0, fy1 - fy0 ) < this.px( 3*this.fs );
            g.fillPolygon( frame, this.brush( t.chartFrame, 60 ) );
            g.strokePolygon( frame, this.pen( t.chartFrame, 0.4 ) );
            if ( small )
            {
               let mx = (fx0 + fx1)/2, my = (fy0 + fy1)/2, rr = this.px( 3*this.fs );
               let pen = this.pen( t.chartFrame, 0.35 );
               g.strokeCircle( mx, my, rr, pen );
               g.pen = pen;
               g.drawLine( mx - rr*1.8, my, mx - rr*1.15, my );
               g.drawLine( mx + rr*1.15, my, mx + rr*1.8, my );
               g.drawLine( mx, my - rr*1.8, mx, my - rr*1.15 );
               g.drawLine( mx, my + rr*1.15, mx, my + rr*1.8 );
               placer.reserve( mx - rr*1.8, my - rr*1.8, rr*3.6, rr*3.6 );
            }
         }

         // --- ラベル ---
         let fL = this.font( 5.6 );
         let fLB = this.font( 5.6, true );
         let pad = this.px( 0.5*this.fs );

         // 枠のラベル
         if ( frame.length > 2 )
         {
            let f = this.font( 6.4, true );
            let s = this.tr( "frameLabel" );
            let w = f.width( s ), h = f.height;
            let mx = (fx0 + fx1)/2, my = (fy0 + fy1)/2;
            let rr = Math.max( (fx1 - fx0), (fy1 - fy0) )/2 + this.px( 0.6 );
            let pos = placer.place( w, h, mx, my, Math.max( rr, this.px( 3.5*this.fs ) ), pad );
            if ( pos )
               this.text( g, f, t.chartFrame, pos.x, pos.y + f.ascent, s );
         }

         // 星座名
         if ( opts.chartConstNames )
         {
            let f = this.font( 6.4, true );
            for ( let c in cat.constCenters )
            {
               let cc = cat.constCenters[c];
               if ( sceAngDist( info.ra, info.dec, cc.ra, cc.dec ) > lim )
                  continue;
               let p = xy( cc.ra, cc.dec );
               if ( !inside( p ) )
                  continue;
               let s = sceConstellationName( c, this.lang );
               let w = f.width( s ), h = f.height;
               let x = Math.max( rect.x0 + 3, Math.min( rect.x1 - w - 3, p.x - w/2 ) );
               let y = Math.max( rect.y0 + 3, Math.min( rect.y1 - h - 3, p.y - h/2 ) );
               if ( placer.fits( x, y, w, h ) )
               {
                  placer.reserve( x, y, w, h );
                  this.text( g, f, t.chartConstName, x, y + f.ascent, s, 220 );
               }
            }
         }

         // 天体ラベル
         dsoLabels.sort( ( a, b ) => (a.o.rank - b.o.rank) || (a.o.sortMag - b.o.sortMag) );
         let fcx = (fx0 + fx1)/2, fcy = (fy0 + fy1)/2;
         let fr = Math.sqrt( (fx1 - fx0)*(fx1 - fx0) + (fy1 - fy0)*(fy1 - fy0) )/2;
         for ( let lb of dsoLabels )
         {
            let o = lb.o;
            // ラベルは主要な天体だけ（記号は描く）: メシエ・コールドウェルと、
            // 通称のある天体のうち明るいか星図に対して大きいもの
            let big = (o.diam || 0)/60 >= fov/30;
            let bright = o.mag !== undefined && o.mag <= ((fov < 12) ? 10 : 8);
            if ( !(o.rank <= 1 || (fov < 30 && o.rank == 2 && (big || bright)) || (fov < 6 && o.rank <= 3)) )
               continue;
            // 撮影範囲の中の天体は、枠の外側にラベルを付ける
            if ( frame.length > 2 && lb.p.x >= fx0 && lb.p.x <= fx1 && lb.p.y >= fy0 && lb.p.y <= fy1 )
               lb = { o: o, p: new Point( fcx, fcy ), r: Math.max( fr, this.px( 3.5*this.fs ) ), color: lb.color };
            let s = sceDsoDesignation( o );
            let name = sceDsoName( o, this.lang );
            if ( name && fov <= 20 && o.rank <= 2 )
               s += " " + name;
            let f = (o.rank == 0) ? fLB : fL;
            let w = f.width( s ), h = f.height;
            let pos = placer.place( w, h, lb.p.x, lb.p.y, lb.r, pad );
            if ( !pos && s != sceDsoDesignation( o ) )
            {
               s = sceDsoDesignation( o );
               w = f.width( s );
               pos = placer.place( w, h, lb.p.x, lb.p.y, lb.r, pad );
            }
            if ( pos )
               this.text( g, f, lb.color, pos.x, pos.y + f.ascent, s );
         }
         for ( let lb of starLabels )
         {
            let s = sceStarName( lb.s, this.lang );
            let w = fL.width( s ), h = fL.height;
            let pos = placer.place( w, h, lb.p.x, lb.p.y, lb.r, pad );
            if ( pos )
               this.text( g, fL, t.chartText, pos.x, pos.y + fL.ascent, s );
         }

         // グリッドの目盛り（左端・下端）
         if ( opts.chartGrid )
         {
            let f = this.font( 5 );
            let off = this.px( 0.7*this.fs );
            for ( let gl of gridLabels )
            {
               let label = (gl.kind == "dec") ? sceFormatDecShort( gl.v ) : sceFormatRAShort( gl.v );
               let pts = gl.pts.map( p => xy( p.ra, p.dec ) );
               for ( let j = 0; j + 1 < pts.length; ++j )
               {
                  let p = pts[j], q = pts[j + 1];
                  if ( !p || !q )
                     continue;
                  if ( gl.kind == "dec" && (p.x - rect.x0)*(q.x - rect.x0) < 0 )
                  {
                     let y = p.y + (rect.x0 - p.x)/(q.x - p.x)*(q.y - p.y);
                     if ( y > rect.y0 + f.height && y < rect.y1 - f.height*2 )
                        this.text( g, f, t.chartText, rect.x0 + off, y - off, label, 200 );
                     break;
                  }
                  if ( gl.kind == "ra" && (p.y - rect.y1)*(q.y - rect.y1) < 0 )
                  {
                     let x = p.x + (rect.y1 - p.y)/(q.y - p.y)*(q.x - p.x);
                     if ( x > rect.x0 + this.px( 10 ) && x < rect.x1 - f.width( label ) - off )
                        this.text( g, f, t.chartText, x + off, rect.y1 - off, label, 200 );
                     break;
                  }
               }
            }
         }
      }
      finally
      {
         g.popState();
      }

      // 枠と注記
      g.pen = this.pen( t.chartEdge, 0.3 );
      g.drawRect( rect.x0, rect.y0, rect.x1, rect.y1 );
      let f = this.font( 5.6 );
      let note = sceFmt( this.tr( "chartField" ), sceFormatAngle( fov ) ) + "   " +
                 sceFmt( this.tr( "chartMag" ), (this.chartStarLimit || 6).toFixed( 1 ) );
      let pad = this.px( 1.2*this.fs );
      g.fillRect( rect.x1 - f.width( note ) - 2*pad, rect.y0, rect.x1, rect.y0 + f.height + pad, this.brush( t.chartBg, 210 ) );
      this.text( g, f, t.chartText, rect.x1 - f.width( note ) - pad, rect.y0 + pad*0.5 + f.ascent, note );
      // 方位（星図は北が上・東が左）
      let fb = this.font( 6, true );
      let ox = rect.x0 + this.px( 5*this.fs ), oy = rect.y0 + this.px( 6.5*this.fs ), L = this.px( 4*this.fs );
      g.pen = this.pen( t.chartText, 0.3 );
      g.drawLine( ox, oy, ox, oy - L );
      g.drawLine( ox, oy, ox - L, oy );
      this.text( g, fb, t.chartText, ox - fb.width( "N" )/2, oy - L - this.px( 0.6 ), "N" );
      this.text( g, fb, t.chartText, ox - L - fb.width( "E" ) - this.px( 0.6 ), oy + fb.ascent/2 - fb.descent/2, "E" );
   }

   chartSymbol( g, grp, p, rad, o, color, xy )
   {
      let pen = this.pen( color, 0.28 );
      switch ( grp )
      {
      case "galaxy":
         {
            let q = (o.axisRatio > 1) ? 1/o.axisRatio : 0.6;
            let pa = (o.pa !== undefined) ? o.pa : 0;
            // 位置角は北から東回り。星図は北が上・東が左なので画面上の角度に直す
            let th = (90 + pa)*SCE_D2R;
            let pts = [];
            for ( let i = 0; i < 32; ++i )
            {
               let t = 2*Math.PI*i/32;
               let ex = rad*Math.cos( t ), ey = rad*q*Math.sin( t );
               pts.push( new Point( p.x - (ex*Math.cos( th ) - ey*Math.sin( th )), p.y - (ex*Math.sin( th ) + ey*Math.cos( th )) ) );
            }
            g.fillPolygon( pts, this.brush( color, 70 ) );
            g.strokePolygon( pts, pen );
         }
         break;
      case "oc":
         g.strokeCircle( p.x, p.y, rad, this.pen( color, 0.28, 255, PenStyle.Dash ) );
         break;
      case "gc":
         g.strokeCircle( p.x, p.y, rad, pen );
         g.pen = pen;
         g.drawLine( p.x - rad, p.y, p.x + rad, p.y );
         g.drawLine( p.x, p.y - rad, p.x, p.y + rad );
         break;
      case "pn":
         g.strokeCircle( p.x, p.y, rad*0.6, pen );
         g.pen = pen;
         g.drawLine( p.x - rad, p.y, p.x - rad*0.6, p.y );
         g.drawLine( p.x + rad*0.6, p.y, p.x + rad, p.y );
         g.drawLine( p.x, p.y - rad, p.x, p.y - rad*0.6 );
         g.drawLine( p.x, p.y + rad*0.6, p.x, p.y + rad );
         break;
      case "nebula":
      case "snr":
         g.fillRect( p.x - rad, p.y - rad, p.x + rad, p.y + rad, this.brush( color, 45 ) );
         g.strokeRect( p.x - rad, p.y - rad, p.x + rad, p.y + rad, pen );
         break;
      default:
         g.strokeCircle( p.x, p.y, rad, pen );
         break;
      }
   }

   // ------------------------------------------------------------------------
   // 基本データ・一覧・メモ
   // ------------------------------------------------------------------------

   drawInfo( g )
   {
      let R = this.layout.info, t = this.theme, lay = R.lay;
      let y0 = this.sectionTitle( g, R.x, R.y, R.w, this.tr( "secInfo" ) );
      let fL = this.font( 6.8 ), fV = this.font( 6.8 );
      let lineH = this.px( this.infoRowMM() );
      let yMax = this.px( R.y + R.h );
      for ( let c = 0; c < lay.columns.length; ++c )
      {
         let x0 = this.px( R.x + c*(lay.colW + lay.colGap) );
         let y = y0;
         for ( let it of lay.columns[c] )
         {
            if ( y + lineH > yMax + 1 )
               break;
            this.text( g, fL, t.sub, x0, y + fV.ascent, it.label );
            for ( let line of it.lines )
            {
               if ( y + lineH > yMax + 1 )
                  break;
               this.text( g, fV, t.text, x0 + this.px( lay.labelW ), y + fV.ascent, line );
               y += lineH;
            }
         }
      }
   }

   /*
    * 天体一覧（縦長の列）。文字は省略せず、名前が入りきらなければ次の行に折り返す。
    */
   drawList( g )
   {
      let R = this.layout.list, t = this.theme;
      let items = this.model.listItems;
      let limit = Math.min( (this.opts.listMax > 0) ? this.opts.listMax : 999, items.length );
      let title = this.tr( "secList" ) + ((items.length > 0) ? " (" + items.length + ")" : "");
      let y0 = this.sectionTitle( g, R.x, R.y, R.w, title );
      let fD = this.font( 6.6, true ), fN = this.font( 6.6 ), fT = this.font( 5.8 );
      if ( items.length == 0 )
      {
         this.text( g, fN, t.sub, this.px( R.x ), y0 + fN.ascent, this.tr( "noObjects" ) );
         return;
      }
      let fs = this.fs;
      let lineH = this.px( 6.6*0.3528*1.42*fs );
      let entryGap = lineH*0.18;
      let colGap = this.px( 5*fs );
      let cols = Math.max( 1, Math.min( 4, Math.floor( (R.w + 5*fs)/(78*fs) ) ) );
      let colW = (this.px( R.w ) - (cols - 1)*colGap)/cols;
      let yMax = this.px( R.y + R.h );
      let mr = fN.ascent*0.22;
      let indent = mr*2 + this.px( 1.2*fs );
      let sp = this.px( 1.8*fs );
      let C = t.dso;

      // 1件分の行構成を決める
      let block = ( it ) =>
      {
         let avail = colW - indent;
         let wD = fD.width( it.desig ), wT = fT.width( it.type );
         let name = it.name || it.alt || "";
         let nameIsAlt = !it.name;
         let wN = name ? fN.width( name ) : 0;
         if ( wD + (name ? sp + wN : 0) + sp + wT <= avail )
            return { lines: 1, oneLine: true, name: name, nameIsAlt: nameIsAlt };
         let typeOwnLine = wD + sp + wT > avail;
         let nameIndent = this.px( 3*fs );
         let nameLines = name ? this.wrap( fN, name, avail - nameIndent ) : [];
         return {
            lines: 1 + (typeOwnLine ? 1 : 0) + nameLines.length, oneLine: false, typeOwnLine: typeOwnLine,
            nameLines: nameLines, nameIndent: nameIndent, nameIsAlt: nameIsAlt
         };
      };

      let col = 0, y = y0, shown = 0;
      for ( let i = 0; i < limit; ++i )
      {
         let it = items[i];
         let b = block( it );
         let h = b.lines*lineH;
         let remainingAfter = items.length - (i + 1);
         // 最後の列では「ほか N 天体」の1行分を残す
         let need = h + ((col == cols - 1 && remainingAfter > 0) ? lineH : 0);
         if ( y + need > yMax )
         {
            if ( col < cols - 1 )
            {
               ++col;
               y = y0;
               need = h + ((col == cols - 1 && remainingAfter > 0) ? lineH : 0);
               if ( y + need > yMax )
                  break;
            }
            else
               break;
         }
         let x = this.px( R.x ) + col*(colW + colGap);
         let base = y + fN.ascent;
         let color = (it.group == "star") ? t.text : (C[it.group] || C.other);
         g.fillCircle( x + mr, base - fN.ascent*0.35, mr, this.brush( color ) );
         let xd = x + indent;
         this.text( g, fD, t.text, xd, base, it.desig );
         let xType = x + colW - fT.width( it.type );
         if ( b.oneLine )
         {
            if ( b.name )
               this.text( g, fN, b.nameIsAlt ? t.sub : t.text, xd + fD.width( it.desig ) + sp, base, b.name );
            this.text( g, fT, t.sub, xType, base, it.type );
         }
         else
         {
            let ly = base;
            if ( b.typeOwnLine )
            {
               ly += lineH;
               this.text( g, fT, t.sub, xType, ly, it.type );
            }
            else
               this.text( g, fT, t.sub, xType, ly, it.type );
            for ( let line of b.nameLines )
            {
               ly += lineH;
               this.text( g, fN, b.nameIsAlt ? t.sub : t.text, xd + b.nameIndent, ly, line );
            }
         }
         y += h + entryGap;
         ++shown;
      }
      let rest = items.length - shown;
      if ( rest > 0 )
      {
         if ( y + lineH > yMax && col < cols - 1 )
         {
            ++col;
            y = y0;
         }
         let x = this.px( R.x ) + col*(colW + colGap);
         this.text( g, fN, t.sub, x + indent, y + fN.ascent, sceFmt( this.tr( "moreObjects" ), rest ) );
      }
   }

   drawMemo( g )
   {
      let R = this.layout.memo, t = this.theme;
      let r = this.rectPx( R );
      g.fillRect( r, this.brush( t.panel ) );
      let pad = this.px( 2*this.fs );
      let y = this.sectionTitle( g, R.x + 2*this.fs, R.y + 1.5*this.fs, R.w - 4*this.fs, this.tr( "secMemo" ) );
      let f = this.font( 7 );
      let lineH = f.lineSpacing*1.15;
      let lines = this.wrap( f, (this.opts.memo || "").trim(), r.width - 2*pad );
      let maxLines = Math.max( 0, Math.floor( (r.y1 - pad*0.5 - y)/lineH ) );
      if ( lines.length > maxLines && maxLines > 0 )
      {
         lines = lines.slice( 0, maxLines );
         lines[maxLines - 1] = this.fit( f, lines[maxLines - 1] + "…", r.width - 2*pad );
      }
      for ( let i = 0; i < lines.length && i < maxLines; ++i )
         this.text( g, f, t.text, r.x0 + pad, y + f.ascent + i*lineH, lines[i] );
   }
}

// ----------------------------------------------------------------------------

/*
 * プレビュー用のビットマップを作る
 */
function sceRenderPreview( model, photoBmp, widthPx )
{
   let renderer = new SceRenderer( model );
   let dpi = widthPx/(renderer.W/25.4);
   let w = Math.round( renderer.W/25.4*dpi ), h = Math.round( renderer.H/25.4*dpi );
   let bmp = new Bitmap( w, h );
   bmp.fill( 0xffffffff );
   let g = new Graphics( bmp );
   try
   {
      renderer.render( g, dpi, photoBmp );
   }
   finally
   {
      g.end();
   }
   return bmp;
}

/*
 * PDF を書き出す。自動圧縮が有効なら、目標サイズに収まるまで写真の解像度を下げて
 * 作り直す（写真以外はベクターなので、サイズはほぼ写真の画素数で決まる）。
 * photoSource(maxLongSide) は指定の長辺サイズの写真ビットマップを返す関数。
 */
function sceWritePDF( filePath, model, photoSource )
{
   let opts = model.opts;
   let renderer = new SceRenderer( model );
   let ps = renderer.photoSizeMM();
   let longMM = Math.max( ps.w, ps.h );
   let maxBytes = opts.maxSizeMB*1024*1024;
   let dpi = opts.imageDpi;
   let dir = File.extractDrive( filePath ) + File.extractDirectory( filePath );
   let result = null;

   for ( let attempt = 1; attempt <= 7; ++attempt )
   {
      let longPx = Math.max( 64, Math.round( longMM/25.4*dpi ) );
      let bmp = photoSource( longPx );
      let tmpPath = File.uniqueFileName( dir, 12, "SkyChartExporter-", ".pdf" );
      let pdf = new PDF( tmpPath );
      pdf.resolution = 300;
      pdf.pageSize = new Rect( 0, 0, renderer.W, renderer.H );
      pdf.title = model.title;
      if ( (opts.observer || "").trim() )
         pdf.author = opts.observer.trim();
      pdf.creator = "SkyChartExporter " + SCE_VERSION + " (PixInsight)";
      let g = new Graphics( pdf );
      try
      {
         renderer.render( g, 300, bmp );
      }
      finally
      {
         g.end();
      }
      let size = File.exists( tmpPath ) ? (new FileInfo( tmpPath )).size : 0;
      if ( size <= 0 )
         throw new Error( sceTr( "errPdfWrite" ) + tmpPath );
      console.writeln( format( "<end><cbr>SkyChartExporter: attempt %d: %d px (%.0f dpi) -> %.2f MB",
                               attempt, Math.max( bmp.width, bmp.height ), dpi, size/1048576 ) );

      let accept = !opts.autoCompress || size <= maxBytes || dpi <= 50 || attempt == 7;
      if ( accept )
      {
         if ( File.exists( filePath ) )
            File.remove( filePath );
         File.move( tmpPath, filePath );
         result = { size: size, dpi: dpi, photoPx: Math.max( bmp.width, bmp.height ), attempts: attempt,
                    overTarget: opts.autoCompress && size > maxBytes };
         break;
      }
      File.remove( tmpPath );
      // 写真の画素数 ∝ dpi² なので、サイズ比の平方根で dpi を下げる
      let factor = Math.max( 0.45, Math.min( 0.9, Math.sqrt( maxBytes/size )*0.92 ) );
      dpi = Math.max( 50, Math.round( dpi*factor ) );
   }
   return result;
}

#endif   // __SCE_Render_jsh
