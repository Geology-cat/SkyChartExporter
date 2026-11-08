// ----------------------------------------------------------------------------
// SkyChartExporter - SCE-Catalog.jsh
//
// PixInsight 同梱のローカルカタログ（Messier / NGC-IC / Caldwell /
// NamedStars / SNR）と星座データ（AnnotateImage 付属の JSON）を読み込み、
// 重複（M ↔ NGC ↔ C）を統合した天体リストを作る。
// ----------------------------------------------------------------------------

#ifndef __SCE_Catalog_jsh
#define __SCE_Catalog_jsh

/*
 * 引用符に対応した CSV 1行の分割
 */
function sceSplitCSV( line )
{
   let fields = [];
   let cur = "";
   let inQuotes = false;
   for ( let i = 0; i < line.length; ++i )
   {
      let ch = line[i];
      if ( inQuotes )
      {
         if ( ch == '"' )
         {
            if ( i + 1 < line.length && line[i + 1] == '"' )
            {
               cur += '"';
               ++i;
            }
            else
               inQuotes = false;
         }
         else
            cur += ch;
      }
      else if ( ch == '"' )
         inQuotes = true;
      else if ( ch == ',' )
      {
         fields.push( cur );
         cur = "";
      }
      else
         cur += ch;
   }
   fields.push( cur );
   return fields;
}

/*
 * CSV ファイルを読み、ヘッダ名をキーとするオブジェクトの配列を返す
 */
function sceReadCSV( path )
{
   if ( !File.exists( path ) )
      throw new Error( sceTr( "errCatalog" ) + path );
   let text = File.readTextFile( path );
   let lines = text.split( /\r?\n/ );
   let header = sceSplitCSV( lines[0] ).map( h => h.trim() );
   let rows = [];
   for ( let i = 1; i < lines.length; ++i )
   {
      let line = lines[i];
      if ( line.trim().length == 0 )
         continue;
      let f = sceSplitCSV( line );
      let r = {};
      for ( let j = 0; j < header.length; ++j )
         r[header[j]] = (j < f.length) ? f[j].trim() : "";
      rows.push( r );
   }
   return rows;
}

function sceNum( s )
{
   if ( s === undefined || s === null )
      return undefined;
   s = ("" + s).trim();
   if ( s.length == 0 )
      return undefined;
   let v = parseFloat( s );
   return isFinite( v ) ? v : undefined;
}

/*
 * "NGC 7000" / "ngc7000" / "IC 434" → "NGC7000" / "IC434"。該当しなければ null。
 */
function sceNormNGC( s )
{
   if ( !s )
      return null;
   let m = /\b(NGC|IC)\s*0*(\d+)\s*([A-Za-z]?)\b/i.exec( s );
   if ( !m )
      return null;
   return m[1].toUpperCase() + m[2] + (m[3] ? m[3].toUpperCase() : "");
}

/*
 * "NGC7000" → "NGC 7000"
 */
function sceSpacedId( id )
{
   let m = /^(NGC|IC)(\d+.*)$/.exec( id );
   return m ? m[1] + " " + m[2] : id;
}

/*
 * 同梱データのパス
 */
function sceCatalogDir()
{
   return CoreApplication.includeDirPath + "/pjsr/astrometry/";
}

function sceConstellationDir()
{
   return CoreApplication.srcDirPath + "/scripts/AnnotateImage/";
}

/*
 * 統合カタログ
 */
class SceCatalog
{
   constructor()
   {
      this.dso = [];        // 星雲・星団・銀河
      this.snr = [];        // 超新星残骸（Green カタログ）
      this.stars = [];      // 恒星
      this.constLines = []; // { c, pts:[{ra,dec}] }
      this.constBorders = []; // { c1, c2, pts:[{ra,dec}] }
      this.constCenters = {}; // c → {ra,dec}
      this.dsoKey = null;     // 読み込み済みの DSO カタログの条件
      this.basicLoaded = false;
      this.source = "pixinsight";   // "stellarium" | "pixinsight"
      this.stellariumDir = "";
      this.stelJa = {};       // Stellarium の日本語訳（英語名 → 日本語名）
      this.gaiaCache = null;
   }

   /*
    * settings: { useStellarium, stellariumDir }
    * Stellarium が見つかればその DSO カタログ（約9.5万天体）を使い、
    * 見つからなければ PixInsight 同梱のカタログを使う。
    */
   load( settings = {} )
   {
      let t0 = Date.now();
      let stelDir = settings.useStellarium ? sceFindStellariumDir( settings.stellariumDir ) : "";
      let key = stelDir || "pixinsight";
      if ( this.dsoKey !== key )
      {
         this.source = "pixinsight";
         this.stellariumDir = "";
         this.stelJa = {};
         this.dso = [];
         this.snr = [];
         if ( stelDir )
         {
            try
            {
               this.loadStellariumDSO( stelDir );
               this.source = "stellarium";
               this.stellariumDir = stelDir;
            }
            catch ( x )
            {
               console.warningln( "SkyChartExporter: Stellarium catalog could not be loaded: " + x );
            }
         }
         if ( this.source != "stellarium" )
         {
            this.loadDSO();
            this.loadSNR();
         }
         this.dsoKey = key;
      }
      if ( !this.basicLoaded )
      {
         this.loadStars();
         this.loadConstellations();
         this.basicLoaded = true;
      }
      // 恒星の日本語名: 辞書 → Stellarium の訳
      for ( let s of this.stars )
         s.ja = s.proper ? (SCE_STAR_NAMES_JA[s.proper] || this.stelJa[s.proper] || "") : "";
      console.writeln( format( "<end><cbr>SkyChartExporter: catalogs ready (%s): %d DSO, %d SNR, %d stars, %d constellation lines (%.2f s)",
                               this.source, this.dso.length, this.snr.length, this.stars.length, this.constLines.length, (Date.now() - t0)/1000 ) );
   }

   /*
    * Stellarium の DSO カタログを読み、名称・順位を付ける
    */
   loadStellariumDSO( dir )
   {
      let r = SceStellarium.loadDSO( dir );
      this.stelJa = r.ja;
      for ( let rec of r.list )
      {
         let ids = rec.ids;
         let keys = [ rec.m, rec.ngc, ids.IC ? "IC" + ids.IC : null, rec.c,
                      ids.Sh2 ? "Sh2-" + ids.Sh2 : null, ids.Cr ? "Cr" + ids.Cr : null, ids.Mel ? "Mel" + ids.Mel : null ];
         for ( let k of keys )
         {
            if ( !k )
               continue;
            let n = SCE_DSO_NAMES[k];
            if ( n )
            {
               if ( n.ja && !rec.ja ) rec.ja = n.ja;
               if ( n.en && !rec.en ) rec.en = n.en;
            }
            if ( rec.diam === undefined && SCE_SIZE_FIXUPS[k] !== undefined )
               rec.diam = SCE_SIZE_FIXUPS[k];
         }
         if ( !rec.ja && rec.jaStel )
            rec.ja = rec.jaStel;
         if ( rec.m && SCE_MESSIER_TYPES[rec.m] && rec.type == "other" )
            rec.type = SCE_MESSIER_TYPES[rec.m];
         // 中心星の種別が付いている星雲（例: Sh2-308）は星雲として扱う
         if ( rec.type == "star" )
         {
            if ( ids.VdB )
               rec.type = "reflectionNebula";
            else if ( ids.Sh2 || ids.LBN || ids.RCW || ids.Ced || ids.DWB )
               rec.type = "nebula";
         }
         rec.rank = rec.m ? 0 : (rec.c ? 1 : ((rec.ja || rec.en) ? 2 : (rec.ngc ? 3 : 4)));
         rec.sortMag = (rec.mag !== undefined) ? rec.mag :
                        ((rec.diam !== undefined) ? 13 - 2.5*Math.log10( Math.max( rec.diam, 0.1 ) ) : 16);
      }
      this.dso = r.list;
      this.snr = [];   // 超新星残骸は DSO 側に含まれる（isSNR）
   }

   // ------------------------------------------------------------------------

   loadDSO()
   {
      let dir = sceCatalogDir();
      let byKey = {};
      let list = [];

      let add = ( key, rec ) =>
      {
         rec.key = key;
         byKey[key] = rec;
         list.push( rec );
         return rec;
      };

      // 1) NGC / IC
      let ngcRows = sceReadCSV( dir + "NGC-IC.csv" );
      for ( let r of ngcRows )
      {
         let id = sceNormNGC( r["id"] ) || r["id"];
         let ra = sceNum( r["alpha"] ), dec = sceNum( r["delta"] );
         if ( ra === undefined || dec === undefined )
            continue;
         if ( byKey[id] )
            continue;
         add( id, {
            m: null, c: null, ngc: id, sup: null,
            ra: ra, dec: dec,
            mag: sceNum( r["magnitude"] ),
            diam: sceNum( r["diameter"] ),
            axisRatio: sceNum( r["axisRatio"] ),
            pa: sceNum( r["posAngle"] ),
            type: sceTypeKey( r["Type"] ),
            en: r["Common name"] || "",
            ja: ""
         } );
      }

      // 2) Messier
      let mRows = sceReadCSV( dir + "Messier.csv" );
      for ( let r of mRows )
      {
         let mid = r["id"].replace( /\s+/g, "" );
         let ngc = sceNormNGC( r["NGC/IC"] );
         let rec = (ngc && byKey[ngc]) ? byKey[ngc] : null;
         let ra = sceNum( r["alpha"] ), dec = sceNum( r["delta"] );
         if ( rec === null )
         {
            rec = add( mid, {
               m: mid, c: null, ngc: ngc, sup: null,
               ra: ra, dec: dec, mag: undefined, diam: undefined, axisRatio: undefined, pa: undefined,
               type: SCE_MESSIER_TYPES[mid] || "other", en: "", ja: ""
            } );
         }
         rec.m = mid;
         // メシエカタログの値を優先する（位置・明るさ・大きさ）
         if ( ra !== undefined && dec !== undefined )
         {
            rec.ra = ra;
            rec.dec = dec;
         }
         let v;
         if ( (v = sceNum( r["magnitude"] )) !== undefined ) rec.mag = v;
         if ( (v = sceNum( r["diameter"] )) !== undefined ) rec.diam = v;
         if ( (v = sceNum( r["axisRatio"] )) !== undefined ) rec.axisRatio = v;
         if ( (v = sceNum( r["posAngle"] )) !== undefined ) rec.pa = v;
         if ( r["Common name"] )
            rec.en = r["Common name"];
         if ( SCE_MESSIER_TYPES[mid] )
            rec.type = SCE_MESSIER_TYPES[mid];
      }

      // 3) Caldwell
      let cRows = sceReadCSV( dir + "Caldwell.csv" );
      for ( let r of cRows )
      {
         let cid = r["id"].replace( /\s+/g, "" );
         let ngc = sceNormNGC( r["Identifiers"] ) || sceNormNGC( r["SIMBAD"] );
         let rec = (ngc && byKey[ngc]) ? byKey[ngc] : null;
         if ( rec === null )
         {
            let ra = sceNum( r["alpha"] ), dec = sceNum( r["delta"] );
            if ( ra === undefined || dec === undefined )
               continue;
            rec = add( cid, {
               m: null, c: cid, ngc: ngc, sup: null,
               ra: ra, dec: dec,
               mag: sceNum( r["magnitude"] ),
               diam: sceNum( r["diameter"] ),
               axisRatio: sceNum( r["axisRatio"] ),
               pa: sceNum( r["posAngle"] ),
               type: sceTypeKey( r["Type"] ),
               en: "", ja: ""
            } );
         }
         rec.c = cid;
         if ( !rec.en && r["Common name"] )
            rec.en = r["Common name"];
      }

      // 4) 補助カタログ
      for ( let s of SCE_SUPPLEMENT )
      {
         if ( byKey[s.id] )
            continue;
         add( s.id, {
            m: null, c: null, ngc: null, sup: s.label,
            ra: s.ra, dec: s.dec, mag: undefined, diam: s.diam,
            axisRatio: s.axisRatio, pa: undefined,
            type: s.type, en: "", ja: ""
         } );
      }

      // 5) 名称辞書と補正値の適用
      for ( let rec of list )
      {
         let keys = [ rec.m, rec.ngc, rec.c, rec.key ];
         for ( let k of keys )
         {
            if ( !k )
               continue;
            let n = SCE_DSO_NAMES[k];
            if ( n )
            {
               if ( n.ja && !rec.ja )
                  rec.ja = n.ja;
               if ( n.en && !rec.en )
                  rec.en = n.en;
            }
         }
         for ( let k of keys )
            if ( k && SCE_SIZE_FIXUPS[k] !== undefined )
            {
               rec.diam = SCE_SIZE_FIXUPS[k];
               break;
            }
         rec.rank = rec.m ? 0 : (rec.c ? 1 : ((rec.ja || rec.en) ? 2 : (rec.ngc ? 3 : 4)));
         rec.sortMag = (rec.mag !== undefined) ? rec.mag :
                        ((rec.diam !== undefined) ? 13 - 2.5*Math.log10( Math.max( rec.diam, 0.1 ) ) : 16);
      }

      this.dso = list;
   }

   loadSNR()
   {
      let rows;
      try
      {
         rows = sceReadCSV( sceCatalogDir() + "SNR.csv" );
      }
      catch ( x )
      {
         console.warningln( "SkyChartExporter: " + x.message );
         return;
      }
      for ( let r of rows )
      {
         let ra = sceNum( r["alpha"] ), dec = sceNum( r["delta"] );
         if ( ra === undefined || dec === undefined )
            continue;
         let name = r["Common name"] || "";
         // "(W30)" のような括弧付き別名や電波源番号は通称として扱わない
         if ( /^\(|^\d|^[0-9A-Z]+\s?[0-9+-.]+$/.test( name ) )
            name = "";
         this.snr.push( {
            key: "SNR " + r["id"], m: null, c: null, ngc: null, sup: "SNR " + r["id"],
            ra: ra, dec: dec, mag: undefined,
            diam: sceNum( r["diameter"] ), axisRatio: sceNum( r["axisRatio"] ), pa: undefined,
            type: "snr", en: name.split( "," )[0].trim(), ja: "", rank: 3, sortMag: 16, isSNR: true
         } );
      }
   }

   /*
    * Gaia（PixInsight の XPSD データベース。SPCC と同じもの）から、指定範囲の
    * 恒星を取得する。使えなければ null。
    * dir: XPSD ファイルのあるフォルダ（空なら Gaia プロセスの設定を使う）
    */
   gaiaStars( ra, dec, radius, magMax, dir )
   {
      let key = [ ra.toFixed( 3 ), dec.toFixed( 3 ), radius.toFixed( 2 ), magMax.toFixed( 2 ), dir || "" ].join( "|" );
      if ( this.gaiaCache && this.gaiaCache.key == key )
         return this.gaiaCache.stars;
      let stars = null;
      try
      {
         stars = SceCatalog.queryGaia( ra, dec, radius, magMax, dir );
      }
      catch ( x )
      {
         console.warningln( "SkyChartExporter: Gaia query failed: " + x );
      }
      this.gaiaCache = { key: key, stars: stars };
      return stars;
   }

   static queryGaia( ra, dec, radius, magMax, dir )
   {
      if ( typeof Gaia == "undefined" )
         return null;
      let releases = [];
      for ( let name of [ "DataRelease_3_SP", "DataRelease_3", "DataRelease_E3", "DataRelease_2" ] )
         if ( Gaia[name] !== undefined )
            releases.push( Gaia[name] );
      let t0 = Date.now();

      let search = ( rel ) =>
      {
         let g = new Gaia;
         g.command = "search";
         g.dataRelease = rel;
         g.centerRA = ra;
         g.centerDec = dec;
         g.radius = Math.min( 180, radius );
         g.magnitudeLow = -1.5;
         g.magnitudeHigh = magMax;
         g.sourceLimit = 500000;
         g.exclusionFlags = 0;
         g.inclusionFlags = 0;
         g.generateTextOutput = false;
         g.verbosity = 0;
         let ok = false;
         try
         {
            ok = g.executeGlobal();
         }
         catch ( x )
         {
            ok = false;
         }
         if ( !ok || !g.sources || g.sources.length == 0 )
            return null;
         let iRA = Gaia.sources_ra, iDec = Gaia.sources_dec, iG = Gaia.sources_magG;
         let S = g.sources;
         let out = new Array( S.length );
         for ( let i = 0; i < S.length; ++i )
            out[i] = { ra: S[i][iRA], dec: S[i][iDec], mag: S[i][iG] };
         return out;
      };

      // 1) PixInsight の Gaia プロセスの設定（SPCC で使っているもの）をそのまま使う
      let out = null;
      for ( let rel of releases )
         if ( (out = search( rel )) !== null )
            break;

      // 2) 使えなければ、指定フォルダの XPSD ファイルを Gaia プロセスに登録して再検索
      if ( out === null && dir && File.directoryExists( dir ) )
      {
         let files = searchDirectory( dir.replace( /[\/\\]+$/, "" ) + "/*.xpsd", false );
         if ( files.length > 0 )
         {
            let name = File.extractName( files[0] ).toLowerCase();
            let rel = /^gdr3sp/.test( name ) ? Gaia.DataRelease_3_SP :
                      (/^gedr3/.test( name ) ? Gaia.DataRelease_E3 :
                      (/^gdr2/.test( name ) ? Gaia.DataRelease_2 : Gaia.DataRelease_3));
            if ( rel !== undefined )
            {
               let c = new Gaia;
               c.command = "configure";
               c.dataRelease = rel;
               c.databaseFilePaths = files.map( f => [ f ] );
               try
               {
                  if ( c.executeGlobal() )
                  {
                     console.writeln( "<end><cbr>SkyChartExporter: Gaia database configured: " + dir );
                     out = search( rel );
                  }
               }
               catch ( x )
               {
                  console.warningln( "SkyChartExporter: Gaia database could not be configured: " + x );
               }
            }
         }
      }
      if ( out !== null )
         console.writeln( format( "<end><cbr>SkyChartExporter: Gaia: %d stars to G=%.1f within %.1f deg (%.2f s)",
                                  out.length, magMax, radius, (Date.now() - t0)/1000 ) );
      return out;
   }

   // ------------------------------------------------------------------------

   loadStars()
   {
      let rows = sceReadCSV( sceCatalogDir() + "NamedStars.csv" );
      for ( let r of rows )
      {
         let ra = sceNum( r["alpha"] ), dec = sceNum( r["delta"] );
         let mag = sceNum( r["magnitude"] );
         if ( ra === undefined || dec === undefined || mag === undefined )
            continue;
         let proper = r["Common name"] || "";
         let parsed = SceCatalog.parseStarId( r["id"] );
         this.stars.push( {
            ra: ra, dec: dec, mag: mag,
            id: r["id"],
            bayer: parsed.label,
            con: parsed.con,
            proper: proper,
            ja: "",
            type: "star"
         } );
      }
      // 明るい順
      this.stars.sort( ( a, b ) => a.mag - b.mag );
   }

   /*
    * "9 alf CMa" → { label: "α CMa", con: "CMA" }
    * "bet01 Cyg" → "β¹ Cyg"、"61 Cyg" → "61 Cyg"、"CG And" → "CG And"
    */
   static parseStarId( id )
   {
      let s = id.trim();
      let m = /^(\d+\s+)?([A-Za-z]+)\.?(\d*)\s+([A-Z][a-zA-Z]{2})$/.exec( s );
      if ( m )
      {
         let key = m[2].toLowerCase();
         let con = m[4].toUpperCase();
         if ( SCE_GREEK[key] )
         {
            let sup = "";
            if ( m[3] )
            {
               let n = parseInt( m[3], 10 );
               for ( let ch of ("" + n) )
                  sup += SCE_SUPERSCRIPT_DIGITS[parseInt( ch, 10 )];
            }
            return { label: SCE_GREEK[key] + sup + " " + m[4], con: con };
         }
         if ( m[2].length == 1 && !m[1] )
            return { label: m[2] + " " + m[4], con: con };
      }
      let f = /^(\d+)\s+([A-Z][a-zA-Z]{2})$/.exec( s );
      if ( f )
         return { label: f[1] + " " + f[2], con: f[2].toUpperCase() };
      let c = /\s([A-Z][a-zA-Z]{2})$/.exec( s );
      return { label: s, con: c ? c[1].toUpperCase() : "" };
   }

   // ------------------------------------------------------------------------

   loadConstellations()
   {
      let dir = sceConstellationDir();
      try
      {
         // ConstellationLines.json の x は「時」単位
         let lines = JSON.parse( File.readTextFile( dir + "ConstellationLines.json" ) );
         let sums = {};
         for ( let l of lines )
         {
            let pts = l.pol.map( p => ({ ra: p.x*15, dec: p.y }) );
            this.constLines.push( { c: l.c, pts: pts } );
            let s = sums[l.c] || (sums[l.c] = { x: 0, y: 0, z: 0, n: 0 });
            for ( let p of pts )
            {
               let cd = Math.cos( p.dec*SCE_D2R );
               s.x += cd*Math.cos( p.ra*SCE_D2R );
               s.y += cd*Math.sin( p.ra*SCE_D2R );
               s.z += Math.sin( p.dec*SCE_D2R );
               ++s.n;
            }
         }
         for ( let c in sums )
         {
            let s = sums[c];
            let r = Math.sqrt( s.x*s.x + s.y*s.y + s.z*s.z );
            if ( r > 0 )
               this.constCenters[c] = {
                  ra: sceNormRA( Math.atan2( s.y, s.x )*SCE_R2D ),
                  dec: Math.asin( s.z/r )*SCE_R2D
               };
         }
      }
      catch ( x )
      {
         console.warningln( "SkyChartExporter: constellation lines not available: " + x.message );
      }

      try
      {
         // ConstellationBorders.json の x は「度」単位
         let borders = JSON.parse( File.readTextFile( dir + "ConstellationBorders.json" ) );
         for ( let b of borders )
            this.constBorders.push( { c1: b.c1, c2: b.c2, pts: b.pol.map( p => ({ ra: p.x, dec: p.y }) ) } );
      }
      catch ( x )
      {
         console.warningln( "SkyChartExporter: constellation borders not available: " + x.message );
      }
   }

   /*
    * 指定座標が属する星座の略号（大文字）を返す。判定できなければ ""。
    *
    * 点を中心とする局所的な立体射影の上で、8方向に半直線を伸ばし、
    * それぞれ最初に交差する境界線の両側の星座を候補とする。自分の星座は
    * すべての半直線の最初の交差に必ず現れるので、全候補の共通部分を取る。
    */
   findConstellation( ra, dec )
   {
      if ( this.constBorders.length == 0 )
         return "";
      let proj = new SceStereoProjection( ra, dec );
      let segs = [];
      for ( let b of this.constBorders )
      {
         let prev = null;
         for ( let p of b.pts )
         {
            let q = (sceAngDist( ra, dec, p.ra, p.dec ) < 100) ? proj.project( p.ra, p.dec ) : null;
            if ( q && prev )
               segs.push( { x1: prev.x, y1: prev.y, x2: q.x, y2: q.y, c1: b.c1, c2: b.c2 } );
            prev = q;
         }
      }
      let candidates = null;
      const N = 12;
      for ( let k = 0; k < N; ++k )
      {
         let th = 2*Math.PI*(k + 0.37)/N;
         let dx = Math.cos( th ), dy = Math.sin( th );
         let bestT = Infinity, best = null;
         for ( let s of segs )
         {
            // 半直線 t*(dx,dy) と線分 (x1,y1)-(x2,y2) の交差
            let ex = s.x2 - s.x1, ey = s.y2 - s.y1;
            let den = dx*ey - dy*ex;
            if ( Math.abs( den ) < 1e-15 )
               continue;
            let t = (s.x1*ey - s.y1*ex)/den;
            let u = (s.x1*dy - s.y1*dx)/den;
            if ( t > 0 && u >= 0 && u <= 1 && t < bestT )
            {
               bestT = t;
               best = s;
            }
         }
         if ( best === null )
            continue;
         let set = {};
         set[best.c1] = true;
         set[best.c2] = true;
         if ( candidates === null )
            candidates = set;
         else
         {
            let next = {};
            for ( let c in candidates )
               if ( set[c] )
                  next[c] = true;
            if ( Object.keys( next ).length > 0 )
               candidates = next;
         }
      }
      if ( candidates === null )
         return "";
      let keys = Object.keys( candidates );
      if ( keys.length == 1 )
         return keys[0];
      // 曖昧な場合は星座中心が最も近いもの
      let best = "", bestD = Infinity;
      for ( let c of keys )
      {
         let cc = this.constCenters[c];
         let d = cc ? sceAngDist( ra, dec, cc.ra, cc.dec ) : 999;
         if ( d < bestD )
         {
            bestD = d;
            best = c;
         }
      }
      return best;
   }
}

/*
 * 表示用ヘルパー
 */
function sceDsoDesignation( o )
{
   if ( o.desig )
      return o.desig;
   if ( o.m )
      return o.m;
   if ( o.ngc )
      return sceSpacedId( o.ngc );
   if ( o.c )
      return o.c;
   return o.sup || o.key;
}

/*
 * 一覧表示用の別名（"NGC 1976" など、主表記以外のカタログ番号）
 */
function sceDsoAltIds( o )
{
   if ( o.alt !== undefined )
      return o.alt;
   let ids = [];
   let primary = sceDsoDesignation( o );
   if ( o.m && o.m != primary ) ids.push( o.m );
   if ( o.ngc && sceSpacedId( o.ngc ) != primary ) ids.push( sceSpacedId( o.ngc ) );
   if ( o.c && o.c != primary ) ids.push( o.c );
   return ids.join( ", " );
}

function sceDsoName( o, lang )
{
   if ( lang == "ja" )
      return o.ja || o.en || "";
   return o.en || "";
}

function sceStarName( s, lang )
{
   if ( lang == "ja" )
      return s.ja || s.proper || s.bayer;
   return s.proper || s.bayer;
}

function sceTypeName( typeKey, lang )
{
   let t = SCE_TYPES[typeKey] || SCE_TYPES.other;
   return (lang == "ja") ? t[0] : t[1];
}

function sceConstellationName( abbr, lang )
{
   let c = SCE_CONSTELLATIONS[(abbr || "").toUpperCase()];
   if ( !c )
      return abbr || "";
   return (lang == "ja") ? c[0] : c[1];
}

#endif   // __SCE_Catalog_jsh
