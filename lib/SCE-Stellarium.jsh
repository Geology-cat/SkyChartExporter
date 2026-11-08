// ----------------------------------------------------------------------------
// SkyChartExporter - SCE-Stellarium.jsh
//
// インストール済みの Stellarium から星雲・星団・銀河のカタログを読み込む。
//
//   nebulae/default/catalog.dat  … DSO カタログ本体（gzip + Qt QDataStream）
//   nebulae/default/names.dat    … 通称（英語）
//   translations/stellarium-sky/ja.qm … 通称の日本語訳（Qt の翻訳ファイル）
//
// データは利用者の Stellarium から実行時に読むだけで、再配布はしない。
// ----------------------------------------------------------------------------

#ifndef __SCE_Stellarium_jsh
#define __SCE_Stellarium_jsh

/*
 * catalog.dat の各レコードにあるカタログ番号の並び（Stellarium 3.x 形式）。
 * s: 文字列型（QString）、それ以外は 32 ビット整数。
 */
var SCE_STEL_IDS = [
   { k: "NGC" }, { k: "IC" }, { k: "M" }, { k: "C" }, { k: "B" }, { k: "Sh2" }, { k: "VdB" }, { k: "RCW" },
   { k: "LDN" }, { k: "LBN" }, { k: "Cr" }, { k: "Mel" }, { k: "PGC" }, { k: "UGC" }, { k: "Ced", s: true },
   { k: "Arp" }, { k: "VV" }, { k: "PK", s: true }, { k: "PNG", s: true }, { k: "SNRG", s: true },
   { k: "ACO", s: true }, { k: "HCG", s: true }, { k: "ESO", s: true }, { k: "VdBH", s: true },
   { k: "DWB" }, { k: "Tr" }, { k: "St" }, { k: "Ru" }, { k: "VdBHa" }
];

/*
 * 表示用の書式と、主表記に使う優先順位
 */
var SCE_STEL_FORMAT = {
   M: "M%s", NGC: "NGC %s", IC: "IC %s", Sh2: "Sh2-%s", C: "C%s", B: "B%s", VdB: "vdB %s", LBN: "LBN %s",
   LDN: "LDN %s", RCW: "RCW %s", Ced: "Ced %s", Cr: "Cr %s", Mel: "Mel %s", Tr: "Tr %s", St: "St %s", Ru: "Ru %s",
   VdBHa: "vdB-Ha %s", DWB: "DWB %s", VdBH: "vdBH %s", Arp: "Arp %s", VV: "VV %s", HCG: "HCG %s",
   ACO: "Abell %s", PK: "PK %s", PNG: "PN G%s", SNRG: "SNR G%s", UGC: "UGC %s", ESO: "ESO %s", PGC: "PGC %s"
};
var SCE_STEL_PRIORITY = [ "M", "NGC", "IC", "Sh2", "C", "B", "VdB", "LBN", "LDN", "RCW", "Ced", "Cr", "Mel",
                          "Tr", "St", "Ru", "VdBHa", "DWB", "VdBH", "Arp", "VV", "HCG", "ACO", "PK", "PNG",
                          "SNRG", "UGC", "PGC", "ESO" ];

// names.dat の接頭辞 → カタログキー
var SCE_STEL_PREFIX = {
   "M": "M", "NGC": "NGC", "IC": "IC", "C": "C", "B": "B", "SH2": "Sh2", "VDB": "VdB", "RCW": "RCW", "LDN": "LDN",
   "LBN": "LBN", "CR": "Cr", "MEL": "Mel", "PGC": "PGC", "UGC": "UGC", "CED": "Ced", "ARP": "Arp", "VV": "VV",
   "PK": "PK", "PNG": "PNG", "SNRG": "SNRG", "ACO": "ACO", "HCG": "HCG", "ESO": "ESO", "VDBH": "VdBH", "DWB": "DWB",
   "TR": "Tr", "ST": "St", "RU": "Ru", "VDBHA": "VdBHa"
};

/*
 * Stellarium の天体種別番号 → 種別キー
 */
function sceStelTypeKey( t )
{
   switch ( t )
   {
   case 0: case 1: case 2: case 21: case 22: return "galaxy";
   case 3:  return "interactingGalaxy";
   case 4: case 25: return "quasar";
   case 5:  return "starCluster";
   case 6:  return "openCluster";
   case 7:  return "globularCluster";
   case 8:  return "association";
   case 9:  return "starCloud";
   case 10: case 14: case 19: case 20: return "nebula";
   case 11: case 26: case 27: return "planetaryNebula";
   case 12: return "darkNebula";
   case 13: return "reflectionNebula";
   case 15: return "emissionNebula";
   case 16: return "clusterNebula";
   case 17: return "hii";
   case 18: case 32: return "snr";
   case 23: return "molecularCloud";
   case 24: case 28: case 29: case 30: return "star";
   case 33: return "galaxyCluster";
   case 34: return "hii";
   }
   return "other";
}

/*
 * Stellarium のデータフォルダ（nebulae/default がある場所）を探す
 */
function sceFindStellariumDir( preferred )
{
   let cands = [];
   if ( preferred && preferred.length > 0 )
      cands.push( preferred );
   cands.push( "/Applications/Stellarium.app/Contents/Resources" );
   cands.push( File.homeDirectory + "/Applications/Stellarium.app/Contents/Resources" );
   cands.push( "C:/Program Files/Stellarium" );
   cands.push( "C:/Program Files (x86)/Stellarium" );
   cands.push( "/usr/share/stellarium" );
   cands.push( "/usr/local/share/stellarium" );
   cands.push( "/snap/stellarium-daily/current/usr/share/stellarium" );
   for ( let c of cands )
   {
      let d = c.replace( /[\/\\]+$/, "" );
      // .app を指定された場合も受け付ける
      if ( /\.app$/i.test( d ) )
         d += "/Contents/Resources";
      if ( File.exists( d + "/nebulae/default/catalog.dat" ) )
         return d;
   }
   return "";
}

/*
 * UTF-8 バイト列 → 文字列
 */
function sceUtf8Decode( b, p, n )
{
   let s = "", end = p + n;
   while ( p < end )
   {
      let c = b[p++];
      if ( c < 0x80 )
         s += String.fromCharCode( c );
      else if ( c < 0xe0 )
         s += String.fromCharCode( ((c & 0x1f) << 6) | (b[p++] & 0x3f) );
      else if ( c < 0xf0 )
      {
         s += String.fromCharCode( ((c & 0x0f) << 12) | ((b[p] & 0x3f) << 6) | (b[p + 1] & 0x3f) );
         p += 2;
      }
      else
      {
         let cp = ((c & 0x07) << 18) | ((b[p] & 0x3f) << 12) | ((b[p + 1] & 0x3f) << 6) | (b[p + 2] & 0x3f);
         p += 3;
         cp -= 0x10000;
         s += String.fromCharCode( 0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff) );
      }
   }
   return s;
}

/*
 * Qt の翻訳ファイル（.qm）から 原文 → 訳文 の辞書を作る
 */
function sceReadQM( path )
{
   let map = {};
   if ( !File.exists( path ) )
      return map;
   let b = File.readFile( path, DataType.Uint8Array );
   let dv = new DataView( b.buffer, b.byteOffset, b.byteLength );
   let utf16 = ( o, n ) =>
   {
      let s = "";
      for ( let i = 0; i + 1 < n; i += 2 )
         s += String.fromCharCode( dv.getUint16( o + i, false ) );
      return s;
   };
   let o = 16;
   while ( o + 5 <= b.length )
   {
      let tag = b[o];
      let len = dv.getUint32( o + 1, false );
      let start = o + 5;
      o = start + len;
      if ( tag != 0x69 )   // メッセージ以外のセクションは不要
         continue;
      let p = start, end = start + len;
      let src = null, tr = null;
      while ( p < end )
      {
         let t = b[p++];
         if ( t == 1 )
         {
            if ( src && tr && tr != src && map[src] === undefined )
               map[src] = tr;
            src = tr = null;
            continue;
         }
         if ( t == 5 )
         {
            p += 4;
            continue;
         }
         let n = dv.getUint32( p, false );
         p += 4;
         if ( n == 0xffffffff )
            continue;
         if ( t == 3 )
            tr = utf16( p, n );
         else if ( t == 6 )
            src = sceUtf8Decode( b, p, n );
         p += n;
      }
   }
   return map;
}

/*
 * names.dat → { "NGC:7000": ["North America Nebula", ...] }
 */
function sceReadStelNames( path )
{
   let map = {};
   if ( !File.exists( path ) )
      return map;
   for ( let line of File.readTextFile( path ).split( /\r?\n/ ) )
   {
      if ( line.length < 21 || line[0] == "#" )
         continue;
      let prefix = line.substring( 0, 5 ).trim().toUpperCase();
      let id = line.substring( 5, 20 ).trim();
      let m = /_\("(.*?)"\)/.exec( line.substring( 20 ) );
      let cat = SCE_STEL_PREFIX[prefix];
      if ( !cat || !m || id.length == 0 )
         continue;
      let key = cat + ":" + SceStellarium.normId( id );
      (map[key] || (map[key] = [])).push( m[1] );
   }
   return map;
}

class SceStellarium
{
   static normId( s )
   {
      s = ("" + s).trim();
      return /^\d+$/.test( s ) ? "" + parseInt( s, 10 ) : s.toUpperCase();
   }

   /*
    * catalog.dat を読み、SceCatalog の天体レコードの配列を返す
    */
   static loadDSO( dir )
   {
      let t0 = Date.now();
      let raw = File.readFile( dir + "/nebulae/default/catalog.dat", DataType.Uint8Array );
      let d = sceGunzip( raw );
      let dv = new DataView( d.buffer, d.byteOffset, d.byteLength );
      let o = 0;
      let qstr = () =>
      {
         let n = dv.getUint32( o, false );
         o += 4;
         if ( n == 0xffffffff || n == 0 )
            return "";
         let s = "";
         for ( let i = 0; i + 1 < n; i += 2 )
            s += String.fromCharCode( dv.getUint16( o + i, false ) );
         o += n;
         return s;
      };
      let version = qstr();
      qstr();   // edition
      if ( !/^3\./.test( version ) )
         console.warningln( "SkyChartExporter: unexpected Stellarium catalog version: " + version );

      let names = sceReadStelNames( dir + "/nebulae/default/names.dat" );
      let ja = sceReadQM( dir + "/translations/stellarium-sky/ja.qm" );

      let list = [];
      let nIds = SCE_STEL_IDS.length;
      let seq = 0;
      while ( o < d.length )
      {
         // 通し番号が 1, 2, 3… と続かなければ形式が想定と違う（誤読を防ぐ）
         if ( o + 108 > d.length || dv.getUint32( o, false ) != ++seq )
            throw new Error( "Unsupported Stellarium catalog format (record " + seq + ")" );
         let ra = dv.getFloat64( o + 4, false );
         let dec = dv.getFloat64( o + 12, false );
         let bMag = dv.getFloat64( o + 20, false );
         let vMag = dv.getFloat64( o + 28, false );
         let otype = dv.getUint32( o + 36, false );
         o += 40;
         qstr();                                  // 形態分類
         let maj = dv.getFloat64( o, false );
         let min = dv.getFloat64( o + 8, false );
         let pa = dv.getInt32( o + 16, false );
         o += 20 + 48;                            // 赤方偏移・視差・距離（不要）
         let ids = {};
         for ( let k = 0; k < nIds; ++k )
         {
            let spec = SCE_STEL_IDS[k];
            if ( spec.s )
            {
               let s = qstr().trim();
               if ( s.length > 0 )
                  ids[spec.k] = s;
            }
            else
            {
               let v = dv.getUint32( o, false );
               o += 4;
               if ( v != 0 )
                  ids[spec.k] = "" + v;
            }
         }
         let primary = null;
         for ( let k of SCE_STEL_PRIORITY )
            if ( ids[k] !== undefined )
            {
               primary = k;
               break;
            }
         if ( primary === null )
            continue;

         let mag = (vMag < 98) ? vMag : ((bMag < 98) ? bMag : undefined);
         // LDN・LBN などの「等級」欄は不透明度や明るさの階級なので、
         // M/NGC/IC/C 番号を持たない星雲類では等級として扱わない
         let tk = sceStelTypeKey( otype );
         if ( !(ids.M || ids.NGC || ids.IC || ids.C) &&
              /^(nebula|emissionNebula|hii|reflectionNebula|darkNebula|molecularCloud|snr|clusterNebula)$/.test( tk ) )
            mag = undefined;
         let rec = {
            key: primary + ":" + ids[primary],
            ids: ids,
            m: ids.M ? "M" + ids.M : null,
            c: ids.C ? "C" + ids.C : null,
            ngc: ids.NGC ? "NGC" + ids.NGC : (ids.IC ? "IC" + ids.IC : null),
            ra: sceNormRA( ra*SCE_R2D ),
            dec: dec*SCE_R2D,
            mag: mag,
            diam: (maj > 0) ? maj*60 : undefined,
            axisRatio: (maj > 0 && min > 0) ? maj/min : undefined,
            pa: (maj > 0 && min > 0 && min < maj) ? pa : undefined,
            type: sceStelTypeKey( otype ),
            en: "",
            ja: "",
            isSNR: ids.SNRG !== undefined || otype == 18 || otype == 32,
            desig: format( SCE_STEL_FORMAT[primary], ids[primary] )
         };
         // 別名（主表記以外を3つまで）
         let alt = [];
         for ( let k of SCE_STEL_PRIORITY )
            if ( k != primary && ids[k] !== undefined && alt.length < 3 )
               alt.push( format( SCE_STEL_FORMAT[k], ids[k] ) );
         rec.alt = alt.join( ", " );
         // 通称（いずれかの番号で names.dat に載っていれば）
         for ( let k of SCE_STEL_PRIORITY )
         {
            if ( ids[k] === undefined )
               continue;
            let n = names[k + ":" + SceStellarium.normId( ids[k] )];
            if ( n )
            {
               rec.en = n[0];
               for ( let e of n )
                  if ( ja[e] )
                  {
                     rec.jaStel = ja[e];
                     break;
                  }
               break;
            }
         }
         list.push( rec );
      }
      if ( o != d.length || seq < 1000 )
         throw new Error( "Unsupported Stellarium catalog format (unexpected end)" );
      console.writeln( format( "<end><cbr>SkyChartExporter: Stellarium catalog %s: %d objects (%.2f s)",
                               version, list.length, (Date.now() - t0)/1000 ) );
      return { list: list, ja: ja };
   }
}

#endif   // __SCE_Stellarium_jsh
