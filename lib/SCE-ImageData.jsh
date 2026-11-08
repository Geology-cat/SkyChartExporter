// ----------------------------------------------------------------------------
// SkyChartExporter - SCE-ImageData.jsh
//
// プレートソルブ済み画像からの情報抽出（中心座標・視野・焦点距離・撮影データ）、
// 画像内の天体の判定、写真のストレッチとビットマップ化。
// ----------------------------------------------------------------------------

#ifndef __SCE_ImageData_jsh
#define __SCE_ImageData_jsh

function sceStripQuotes( s )
{
   if ( s === undefined || s === null )
      return "";
   return ("" + s).trim().replace( /^'(.*)'$/, "$1" ).trim();
}

/*
 * "+35 30 00" / "35:30:00" / "35.5" → 35.5
 */
function sceParseSexagesimal( s )
{
   s = sceStripQuotes( s );
   if ( s.length == 0 )
      return undefined;
   let parts = s.split( /[\s:]+/ ).filter( p => p.length > 0 );
   if ( parts.length == 1 )
   {
      let v = parseFloat( parts[0] );
      return isFinite( v ) ? v : undefined;
   }
   let neg = /^-/.test( parts[0] );
   let v = 0;
   for ( let i = 0, f = 1; i < parts.length && i < 3; ++i, f *= 60 )
   {
      let x = Math.abs( parseFloat( parts[i] ) );
      if ( !isFinite( x ) )
         return undefined;
      v += x/f;
   }
   return neg ? -v : v;
}

class SceImageInfo
{
   constructor( window )
   {
      this.window = window;
      let view = window.mainView;
      this.id = view.id;
      this.filePath = window.filePath || "";
      let image = view.image;
      this.width = image.width;
      this.height = image.height;
      this.isColor = image.isColor;

      this.kw = {};
      for ( let k of window.keywords )
         if ( this.kw[k.name] === undefined )
            this.kw[k.name] = sceStripQuotes( k.value );

      this.summary = {};
      try
      {
         let text = window.astrometricSolutionSummary();
         for ( let line of text.split( /\r?\n/ ) )
         {
            let m = /^\s*([A-Za-z][A-Za-z ]*?)\s*\.{2,}\s*(.+?)\s*$/.exec( line );
            if ( m )
               this.summary[m[1]] = m[2];
         }
      }
      catch ( x )
      {
      }

      this.computeGeometry();
      this.readAcquisitionData();
   }

   prop( id )
   {
      let view = this.window.mainView;
      try
      {
         if ( view.hasProperty( id ) )
            return view.propertyValue( id );
      }
      catch ( x )
      {
      }
      return undefined;
   }

   kwNum( name )
   {
      let v = this.kw[name];
      if ( v === undefined || v.length == 0 )
         return undefined;
      let x = parseFloat( v );
      return isFinite( x ) ? x : undefined;
   }

   kwStr( name )
   {
      let v = this.kw[name];
      return (v !== undefined && v.length > 0) ? v : "";
   }

   toSky( x, y )
   {
      let p = this.window.imageToCelestial( x, y );
      return p ? { ra: p.x, dec: p.y } : null;
   }

   computeGeometry()
   {
      let w = this.width, h = this.height;
      let cx = w/2, cy = h/2;
      let c = this.toSky( cx, cy );
      if ( !c )
         throw new Error( sceTr( "errCenter" ) );
      this.ra = c.ra;
      this.dec = c.dec;

      // 画素スケール（"/px）
      let d = Math.max( 2, Math.min( 100, Math.min( w, h )/4 ) );
      let a1 = this.toSky( cx - d/2, cy ), a2 = this.toSky( cx + d/2, cy );
      let b1 = this.toSky( cx, cy - d/2 ), b2 = this.toSky( cx, cy + d/2 );
      let sx = sceAngDist( a1.ra, a1.dec, a2.ra, a2.dec )/d;
      let sy = sceAngDist( b1.ra, b1.dec, b2.ra, b2.dec )/d;
      this.scaleDeg = (sx + sy)/2;          // 度/px
      this.scaleArcsec = this.scaleDeg*3600;

      // 視野（中心を通る線に沿って積算）
      let pathLength = ( x0, y0, x1, y1 ) =>
      {
         let n = 16, L = 0, prev = this.toSky( x0, y0 );
         for ( let i = 1; i <= n; ++i )
         {
            let p = this.toSky( x0 + (x1 - x0)*i/n, y0 + (y1 - y0)*i/n );
            if ( p && prev )
               L += sceAngDist( prev.ra, prev.dec, p.ra, p.dec );
            prev = p;
         }
         return L;
      };
      this.fovW = pathLength( 0, cy, w, cy );
      this.fovH = pathLength( cx, 0, cx, h );

      // 対角の視野（35mm判換算の焦点距離の推定に使う）
      let c0 = this.toSky( 0, 0 ), c1 = this.toSky( w, h );
      this.fovDiag = (c0 && c1) ? sceAngDist( c0.ra, c0.dec, c1.ra, c1.dec ) : Math.sqrt( this.fovW*this.fovW + this.fovH*this.fovH );

      // 中心から最も遠い画像上の点までの角距離
      this.radius = 0;
      for ( let p of [ [0, 0], [w, 0], [0, h], [w, h], [cx, 0], [cx, h], [0, cy], [w, cy] ] )
      {
         let s = this.toSky( p[0], p[1] );
         if ( s )
            this.radius = Math.max( this.radius, sceAngDist( this.ra, this.dec, s.ra, s.dec ) );
      }

      // 画像の外周（天球座標）
      this.boundsRD = [];
      let N = 24;
      let edge = [];
      for ( let i = 0; i < N; ++i ) edge.push( [w*i/N, 0] );
      for ( let i = 0; i < N; ++i ) edge.push( [w, h*i/N] );
      for ( let i = 0; i < N; ++i ) edge.push( [w*(N - i)/N, h] );
      for ( let i = 0; i < N; ++i ) edge.push( [0, h*(N - i)/N] );
      for ( let p of edge )
      {
         let s = this.toSky( p[0], p[1] );
         if ( s )
            this.boundsRD.push( s );
      }

      // 画像の「上」「右」方向の位置角と鏡像の判定
      let dd = Math.min( 50, h/4 );
      let up = this.toSky( cx, cy - dd ), right = this.toSky( cx + dd, cy );
      this.upPA = scePositionAngle( this.ra, this.dec, up.ra, up.dec );
      let rightPA = scePositionAngle( this.ra, this.dec, right.ra, right.dec );
      let diff = (rightPA - this.upPA + 360) % 360;
      this.flipped = Math.abs( diff - 90 ) < 45;

      // PixInsight の表示に合わせた回転角
      this.rotation = undefined;
      if ( this.summary["Rotation"] )
      {
         let m = /([-+]?\d+(\.\d+)?)/.exec( this.summary["Rotation"] );
         if ( m )
         {
            this.rotation = parseFloat( m[1] );
            if ( /flipped/i.test( this.summary["Rotation"] ) )
               this.flipped = true;
         }
      }
      if ( this.rotation === undefined )
         this.rotation = this.upPA;

      this.referenceSystem = this.summary["Reference system"] || this.kwStr( "RADESYS" ) || "ICRS";
      this.projectionName = this.summary["Projection"] || "";
   }

   readAcquisitionData()
   {
      // 焦点距離（キーワード/プロパティの値）
      this.focalNominal = this.kwNum( "FOCALLEN" );
      if ( this.focalNominal === undefined )
      {
         let f = this.prop( "Instrument:Telescope:FocalLength" ); // m
         if ( typeof f == "number" && f > 0 )
            this.focalNominal = f*1000;
      }
      if ( !(this.focalNominal > 0) )
         this.focalNominal = undefined;

      // 画素サイズ（µm）
      this.pixelSize = this.kwNum( "XPIXSZ" );
      if ( this.pixelSize === undefined )
      {
         let p = this.prop( "Instrument:Sensor:XPixelSize" );
         if ( typeof p == "number" && p > 0 )
            this.pixelSize = p;
      }
      if ( this.pixelSize === undefined && this.summary["Pixel size"] )
      {
         let v = parseFloat( this.summary["Pixel size"] );
         if ( v > 0 )
            this.pixelSize = v;
      }
      // ビニング
      let xb = this.kwNum( "XBINNING" );
      this.binning = (xb > 1) ? xb : 1;

      this.computeEffectiveFocal();

      // 撮影日時
      this.dateObs = null;
      let t = this.prop( "Observation:Time:Start" );
      if ( t )
         this.dateObs = sceParseDateTime( t );
      if ( !this.dateObs )
         this.dateObs = sceParseDateTime( this.kw["DATE-BEG"] ) || sceParseDateTime( this.kw["DATE-OBS"] );
      if ( !this.dateObs && this.summary["Observation start time"] )
         this.dateObs = sceParseDateTime( this.summary["Observation start time"].replace( " UTC", "Z" ).replace( " ", "T" ) );

      // 露出
      this.exposure = this.kwNum( "EXPTIME" );
      if ( this.exposure === undefined )
         this.exposure = this.kwNum( "EXPOSURE" );
      if ( this.exposure === undefined )
      {
         let e = this.prop( "Instrument:ExposureTime" );
         if ( typeof e == "number" && e > 0 )
            this.exposure = e;
      }
      this.nSubs = this.kwNum( "NCOMBINE" );

      let propStr = id =>
      {
         let v = this.prop( id );
         return (v !== undefined && v !== null) ? ("" + v).trim() : "";
      };
      this.telescope = this.kwStr( "TELESCOP" ) || propStr( "Instrument:Telescope:Name" );
      this.camera = this.kwStr( "INSTRUME" ) || propStr( "Instrument:Camera:Name" );
      this.filter = this.kwStr( "FILTER" ) || propStr( "Instrument:Filter:Name" );
      this.object = this.kwStr( "OBJECT" ) || propStr( "Observation:Object:Name" );
      this.observer = this.kwStr( "OBSERVER" ) || propStr( "Observer:Name" );

      // 撮影地
      this.siteLat = undefined;
      this.siteLon = undefined;
      let lat = this.prop( "Observation:Location:Latitude" ), lon = this.prop( "Observation:Location:Longitude" );
      if ( typeof lat == "number" && typeof lon == "number" )
      {
         this.siteLat = lat;
         this.siteLon = lon;
      }
      else
      {
         let la = sceParseSexagesimal( this.kw["SITELAT"] || this.kw["OBSGEO-B"] || "" );
         let lo = sceParseSexagesimal( this.kw["SITELONG"] || this.kw["OBSGEO-L"] || "" );
         if ( la !== undefined && lo !== undefined )
         {
            this.siteLat = la;
            this.siteLon = lo;
         }
      }
   }

   /*
    * 焦点距離を決める。
    *
    * プレートソルブで得られるのは画素スケール（″/px）なので、焦点距離には
    * 画素サイズが必要: 焦点距離[mm] = 206.265 × 画素サイズ[µm] × ビニング ÷ 画素スケール
    * 画素サイズは 手入力 → 画像の記録 → カメラごとの前回入力値 → カメラ名からの推定
    * の順に探す。焦点距離そのものの手入力と FOCALLEN の記録はそれより優先する。
    *
    * 結果: this.focal = { value, source, px, pxSource }（不明なら value は undefined）
    */
   resolveFocal( opts )
   {
      let cam = this.camera;
      let px = 0, pxSource = "";
      if ( opts.pixelOverride > 0 )
      {
         px = opts.pixelOverride;
         pxSource = "manual";
      }
      else if ( this.pixelSize > 0 )
      {
         px = this.pixelSize;
         pxSource = "header";
      }
      else if ( cam && opts.cameraPixelSizes && opts.cameraPixelSizes[cam] > 0 )
      {
         px = opts.cameraPixelSizes[cam];
         pxSource = "remembered";
      }
      else if ( cam && (px = scePixelSizeForCamera( cam )) > 0 )
         pxSource = "camera";
      else
         px = 0;

      this.focalEffective = (px > 0 && this.scaleArcsec > 0) ? 206.264806*px*this.binning/this.scaleArcsec : undefined;
      let f = { value: undefined, source: "", px: px, pxSource: pxSource };
      if ( opts.focalOverride > 0 )
      {
         f.value = opts.focalOverride;
         f.source = "manual";
      }
      else if ( this.focalNominal > 0 )
      {
         f.value = this.focalNominal;
         f.source = "header";
      }
      else if ( this.focalEffective > 0 )
      {
         f.value = this.focalEffective;
         f.source = "computed";
      }
      // 35mm判換算（対角 43.27 mm のセンサーで同じ対角視野になる焦点距離）
      let half = Math.min( 89, this.fovDiag/2 )*SCE_D2R;
      f.equiv35 = (half > 0) ? 43.267/(2*Math.tan( half )) : undefined;
      if ( !(f.value > 0) && f.equiv35 > 0 )
      {
         // 画素サイズが分からなければ、視野から35mm判換算でだいたいの値を出す
         f.value = f.equiv35;
         f.source = "fov35";
      }
      this.focal = f;
      return f;
   }

   /*
    * 画素スケールと画素サイズから求めた実効焦点距離（画素サイズ不明なら undefined）
    */
   computeEffectiveFocal( pixelSizeOverride )
   {
      let px = (pixelSizeOverride > 0) ? pixelSizeOverride : this.pixelSize;
      this.focalEffective = (px > 0 && this.scaleArcsec > 0) ?
            206.264806*px*this.binning/this.scaleArcsec : undefined;
      return this.focalEffective;
   }

   /*
    * 天体の配列を画像座標に変換し、画像に写っているものだけを返す。
    * 返り値: [{ o, x, y }]
    */
   findInImage( objects, sizeOf )
   {
      let cand = [];
      for ( let o of objects )
      {
         let r = (sizeOf ? sizeOf( o ) : 0)/2;     // 度
         if ( sceAngDist( this.ra, this.dec, o.ra, o.dec ) <= this.radius + r + 0.02 )
            cand.push( o );
      }
      if ( cand.length == 0 )
         return [];

      let eq = new Float64Array( cand.length*2 );
      for ( let i = 0; i < cand.length; ++i )
      {
         eq[2*i] = cand[i].ra;
         eq[2*i + 1] = cand[i].dec;
      }
      let xy = this.window.celestialToImage( eq );

      let result = [];
      let w = this.width, h = this.height;
      for ( let i = 0; i < cand.length; ++i )
      {
         let x = xy[2*i], y = xy[2*i + 1];
         if ( !isFinite( x ) || !isFinite( y ) )
            continue;
         let o = cand[i];
         let rpx = (sizeOf ? sizeOf( o ) : 0)/2/this.scaleDeg;
         let inside = x >= 0 && y >= 0 && x < w && y < h;
         if ( !inside && rpx > 0 )
            inside = x >= -rpx && y >= -rpx && x < w + rpx && y < h + rpx;
         if ( !inside )
            continue;
         // 外挿による誤検出の防止（往復変換の一致を確認）
         let back = this.toSky( x, y );
         if ( !back || sceAngDist( back.ra, back.dec, o.ra, o.dec ) > Math.max( 10*this.scaleDeg, 1/60 ) )
            continue;
         result.push( { o: o, x: x, y: y } );
      }
      return result;
   }
}

/*
 * STF が恒等変換かどうか
 */
function sceIsIdentitySTF( stf )
{
   if ( !stf )
      return true;
   for ( let c of stf )
      if ( Math.abs( c[0] - 0.5 ) > 1e-6 || Math.abs( c[1] ) > 1e-6 || Math.abs( c[2] - 1 ) > 1e-6 ||
           Math.abs( c[3] ) > 1e-6 || Math.abs( c[4] - 1 ) > 1e-6 )
         return false;
   return true;
}

/*
 * 写真を縮小・ストレッチしてビットマップにする。
 * stretchMode: "auto" | "none" | "stf" | "autostf"
 */
function sceRenderPhoto( window, maxLongSide, stretchMode )
{
   let src = window.mainView.image;
   let scale = Math.min( 1, maxLongSide/Math.max( src.width, src.height ) );
   let img = new Image( src );
   try
   {
      if ( scale < 0.999 )
         img.resample( scale );

      let viewSTF = window.mainView.stf;
      let stf = null;
      switch ( stretchMode )
      {
      case "none":
         break;
      case "stf":
         if ( !sceIsIdentitySTF( viewSTF ) )
            stf = viewSTF;
         break;
      case "autostf":
         stf = img.computeAutoStretch();
         break;
      default: // auto
         if ( !sceIsIdentitySTF( viewSTF ) )
            stf = viewSTF;
         else if ( img.median() < 0.08 )   // リニア画像とみなす
            stf = img.computeAutoStretch();
         break;
      }
      if ( stf )
         img.applyDisplayFunction( stf );
      return img.render( 1, false );
   }
   finally
   {
      try { img.free(); } catch ( x ) {}
   }
}

#endif   // __SCE_ImageData_jsh
