// ----------------------------------------------------------------------------
// SkyChartExporter - SCE-Astro.jsh
//
// 球面天文の計算（角距離・投影）と、座標・日時の書式化。
// ----------------------------------------------------------------------------

#ifndef __SCE_Astro_jsh
#define __SCE_Astro_jsh

var SCE_D2R = Math.PI/180;
var SCE_R2D = 180/Math.PI;

/*
 * 赤経を [0,360) に正規化する
 */
function sceNormRA( a )
{
   a %= 360;
   return (a < 0) ? a + 360 : a;
}

/*
 * 2点間の角距離（度）。ハバーサイン公式で小角でも精度を保つ。
 */
function sceAngDist( ra1, dec1, ra2, dec2 )
{
   let d1 = dec1*SCE_D2R, d2 = dec2*SCE_D2R;
   let sdd = Math.sin( (d2 - d1)/2 );
   let sda = Math.sin( (ra2 - ra1)*SCE_D2R/2 );
   let h = sdd*sdd + Math.cos( d1 )*Math.cos( d2 )*sda*sda;
   return 2*Math.asin( Math.min( 1, Math.sqrt( h ) ) )*SCE_R2D;
}

/*
 * (ra0,dec0) から見た (ra,dec) の位置角（度、北から東回り）
 */
function scePositionAngle( ra0, dec0, ra, dec )
{
   let d0 = dec0*SCE_D2R, d = dec*SCE_D2R, da = (ra - ra0)*SCE_D2R;
   let y = Math.sin( da )*Math.cos( d );
   let x = Math.cos( d0 )*Math.sin( d ) - Math.sin( d0 )*Math.cos( d )*Math.cos( da );
   let pa = Math.atan2( y, x )*SCE_R2D;
   return (pa < 0) ? pa + 360 : pa;
}

/*
 * (ra0,dec0) から位置角 pa の方向へ角距離 dist（度）だけ進んだ点
 */
function sceOffsetPoint( ra0, dec0, pa, dist )
{
   let d0 = dec0*SCE_D2R, r = dist*SCE_D2R, p = pa*SCE_D2R;
   let sd = Math.sin( d0 )*Math.cos( r ) + Math.cos( d0 )*Math.sin( r )*Math.cos( p );
   let d = Math.asin( Math.max( -1, Math.min( 1, sd ) ) );
   let da = Math.atan2( Math.sin( p )*Math.sin( r )*Math.cos( d0 ),
                        Math.cos( r ) - Math.sin( d0 )*sd );
   return { ra: sceNormRA( ra0 + da*SCE_R2D ), dec: d*SCE_R2D };
}

/*
 * 大円に沿って2点間を補間した点列（両端を含む）
 */
function sceGreatCircleSamples( ra1, dec1, ra2, dec2, maxStepDeg )
{
   let dist = sceAngDist( ra1, dec1, ra2, dec2 );
   let n = Math.max( 1, Math.ceil( dist/maxStepDeg ) );
   if ( n == 1 )
      return [ { ra: ra1, dec: dec1 }, { ra: ra2, dec: dec2 } ];
   let pa = scePositionAngle( ra1, dec1, ra2, dec2 );
   let pts = [ { ra: ra1, dec: dec1 } ];
   for ( let i = 1; i < n; ++i )
      pts.push( sceOffsetPoint( ra1, dec1, pa, dist*i/n ) );
   pts.push( { ra: ra2, dec: dec2 } );
   return pts;
}

/*
 * 立体射影（ステレオ投影）。星図の描画に使う。
 * project() は東を +x、北を +y とする単位（2tan(c/2)）で返す。
 */
class SceStereoProjection
{
   constructor( ra0, dec0 )
   {
      this.ra0 = ra0;
      this.dec0 = dec0;
      this.sd0 = Math.sin( dec0*SCE_D2R );
      this.cd0 = Math.cos( dec0*SCE_D2R );
   }

   project( ra, dec )
   {
      let d = dec*SCE_D2R, da = (ra - this.ra0)*SCE_D2R;
      let sd = Math.sin( d ), cd = Math.cos( d ), cda = Math.cos( da );
      let cosc = this.sd0*sd + this.cd0*cd*cda;
      if ( cosc <= -0.95 )
         return null;
      let k = 2/(1 + cosc);
      return {
         x: k*cd*Math.sin( da ),
         y: k*(this.cd0*sd - this.sd0*cd*cda),
         cosc: cosc
      };
   }

   // 中心から角距離 c（度）の点の投影半径
   static radiusForAngle( cDeg )
   {
      return 2*Math.tan( cDeg*SCE_D2R/2 );
   }
}

/*
 * 数値を指定桁でゼロ埋め
 */
function scePad( n, w )
{
   let s = "" + n;
   while ( s.length < w )
      s = "0" + s;
   return s;
}

/*
 * 赤経（度）→ "05h 35m 17.3s"
 */
function sceFormatRA( raDeg, secDecimals = 1 )
{
   let h = sceNormRA( raDeg )/15;
   let f = Math.pow( 10, secDecimals );
   let totalSec = Math.round( h*3600*f )/f;
   if ( totalSec >= 86400 )
      totalSec -= 86400;
   let hh = Math.floor( totalSec/3600 );
   let mm = Math.floor( (totalSec - hh*3600)/60 );
   let ss = totalSec - hh*3600 - mm*60;
   let ssStr = ss.toFixed( secDecimals );
   if ( ss < 10 )
      ssStr = "0" + ssStr;
   return scePad( hh, 2 ) + "h " + scePad( mm, 2 ) + "m " + ssStr + "s";
}

/*
 * 赤緯（度）→ "−05° 23′ 28″"
 */
function sceFormatDec( decDeg, secDecimals = 0 )
{
   let sign = (decDeg < 0) ? "−" : "+";
   let f = Math.pow( 10, secDecimals );
   let totalSec = Math.round( Math.abs( decDeg )*3600*f )/f;
   let dd = Math.floor( totalSec/3600 );
   let mm = Math.floor( (totalSec - dd*3600)/60 );
   let ss = totalSec - dd*3600 - mm*60;
   let ssStr = ss.toFixed( secDecimals );
   if ( ss < 10 )
      ssStr = "0" + ssStr;
   return sign + scePad( dd, 2 ) + "° " + scePad( mm, 2 ) + "′ " + ssStr + "″";
}

/*
 * 角度（度）を読みやすい単位で
 */
function sceFormatAngle( deg )
{
   if ( deg >= 1 )
      return deg.toFixed( deg >= 10 ? 1 : 2 ) + "°";
   let am = deg*60;
   if ( am >= 1 )
      return am.toFixed( am >= 10 ? 1 : 2 ) + "′";
   return (deg*3600).toFixed( 1 ) + "″";
}

/*
 * 星図のグリッド用: 赤経（度）→ "5h30m" 形式
 */
function sceFormatRAShort( raDeg )
{
   let totalMin = Math.round( sceNormRA( raDeg )/15*60 );
   if ( totalMin >= 1440 )
      totalMin -= 1440;
   let h = Math.floor( totalMin/60 ), m = totalMin - h*60;
   return (m == 0) ? h + "h" : h + "h" + scePad( m, 2 ) + "m";
}

function sceFormatDecShort( decDeg )
{
   let totalMin = Math.round( decDeg*60 );
   if ( totalMin == 0 )
      return "0°";
   let sign = (totalMin < 0) ? "−" : "+";
   totalMin = Math.abs( totalMin );
   let d = Math.floor( totalMin/60 ), m = totalMin - d*60;
   return (m == 0) ? sign + d + "°" : sign + d + "°" + scePad( m, 2 ) + "′";
}

/*
 * ユリウス日 → { y, mo, d, h, mi, s }（UTC）
 */
function sceJDToCalendar( jd )
{
   let z = Math.floor( jd + 0.5 );
   let f = jd + 0.5 - z;
   let a = z;
   if ( z >= 2299161 )
   {
      let alpha = Math.floor( (z - 1867216.25)/36524.25 );
      a = z + 1 + alpha - Math.floor( alpha/4 );
   }
   let b = a + 1524;
   let c = Math.floor( (b - 122.1)/365.25 );
   let d = Math.floor( 365.25*c );
   let e = Math.floor( (b - d)/30.6001 );
   let day = b - d - Math.floor( 30.6001*e );
   let month = (e < 14) ? e - 1 : e - 13;
   let year = (month > 2) ? c - 4716 : c - 4715;
   let secs = Math.round( f*86400 );
   if ( secs >= 86400 )
      secs = 86399;
   let h = Math.floor( secs/3600 );
   let mi = Math.floor( (secs - h*3600)/60 );
   let s = secs - h*3600 - mi*60;
   return { y: year, mo: month, d: day, h: h, mi: mi, s: s };
}

/*
 * Date または ISO 8601 文字列 → { y, mo, d, h, mi, s }（UTC）。失敗時 null。
 */
function sceParseDateTime( v )
{
   if ( v === null || v === undefined )
      return null;
   let date = null;
   if ( v instanceof Date )
      date = v;
   else
   {
      let s = ("" + v).trim().replace( /^'|'$/g, "" ).trim();
      if ( s.length == 0 )
         return null;
      // タイムゾーン指定が無い FITS の日時は UTC とみなす
      if ( /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/.test( s ) )
         s += "Z";
      else if ( /^\d{4}-\d{2}-\d{2}$/.test( s ) )
         s += "T00:00:00Z";
      date = new Date( s );
   }
   if ( date === null || isNaN( date.getTime() ) )
      return null;
   return {
      y: date.getUTCFullYear(), mo: date.getUTCMonth() + 1, d: date.getUTCDate(),
      h: date.getUTCHours(), mi: date.getUTCMinutes(), s: date.getUTCSeconds()
   };
}

function sceFormatDateTime( c, withTime = true )
{
   if ( !c )
      return "";
   let s = c.y + "-" + scePad( c.mo, 2 ) + "-" + scePad( c.d, 2 );
   if ( withTime )
      s += " " + scePad( c.h, 2 ) + ":" + scePad( c.mi, 2 ) + " UTC";
   return s;
}

/*
 * 秒 → "3h 20m" / "5m 30s" / "300s"
 */
function sceFormatDuration( sec )
{
   if ( !(sec > 0) )
      return "";
   if ( sec < 60 )
      return (Math.round( sec*10 )/10) + "s";
   let total = Math.round( sec );
   let h = Math.floor( total/3600 );
   let m = Math.floor( (total - h*3600)/60 );
   let s = total - h*3600 - m*60;
   if ( h > 0 )
      return h + "h " + scePad( m, 2 ) + "m";
   return m + "m" + ((s > 0) ? " " + scePad( s, 2 ) + "s" : "");
}

/*
 * 「きりの良い」刻み幅を候補から選ぶ: span/step がおよそ target 本になるもの
 */
function sceNiceStep( span, target, candidates )
{
   let best = candidates[candidates.length - 1];
   for ( let i = 0; i < candidates.length; ++i )
      if ( span/candidates[i] <= target )
      {
         best = candidates[i];
         break;
      }
   return best;
}

// 赤緯グリッドの候補（度）
var SCE_DEC_STEPS = [ 1/60, 2/60, 5/60, 10/60, 15/60, 20/60, 30/60, 1, 2, 5, 10, 15, 20, 30 ];
// 赤経グリッドの候補（度。時間単位 1m = 0.25°）
var SCE_RA_STEPS = [ 5/240, 10/240, 15/240, 30/240, 1/4, 2/4, 5/4, 10/4, 15/4, 20/4, 30/4, 15, 30, 45 ];

#endif   // __SCE_Astro_jsh
