// ----------------------------------------------------------------------------
// SkyChartExporter - SCE-Options.jsh
//
// 設定値とその保存（PixInsight の Settings に JSON として保存）、
// アノテーションの書き込み量プリセット。
// ----------------------------------------------------------------------------

#ifndef __SCE_Options_jsh
#define __SCE_Options_jsh

var SCE_SETTINGS_KEY = "SkyChartExporter/options";

/*
 * アノテーションの書き込み量プリセット
 */
var SCE_PRESET_KEYS = [ "none", "minimal", "standard", "detailed", "all" ];
var SCE_PRESETS = {
   none: {
      aMessier: false, aCaldwell: false, aNamed: false, aNGC: false, aNGCMag: 12, aSNR: false,
      aBrightNeb: false, aDarkNeb: false, aPN: false, aClusters: false, aGalaxies: false, aGalaxyMag: 14,
      aGalaxyClusters: false,
      aStars: false, aStarMag: 4, aConstLines: false, aConstBorders: false, aConstNames: false,
      aGrid: false, aCompass: false
   },
   minimal: {
      aMessier: true, aCaldwell: true, aNamed: true, aNGC: false, aNGCMag: 10, aSNR: false,
      aBrightNeb: false, aDarkNeb: false, aPN: false, aClusters: false, aGalaxies: false, aGalaxyMag: 12,
      aGalaxyClusters: false,
      aStars: true, aStarMag: 2.5, aConstLines: false, aConstBorders: false, aConstNames: false,
      aGrid: false, aCompass: true
   },
   standard: {
      aMessier: true, aCaldwell: true, aNamed: true, aNGC: true, aNGCMag: 12, aSNR: true,
      aBrightNeb: true, aDarkNeb: false, aPN: false, aClusters: true, aGalaxies: false, aGalaxyMag: 13,
      aGalaxyClusters: false,
      aStars: true, aStarMag: 4.5, aConstLines: true, aConstBorders: false, aConstNames: false,
      aGrid: true, aCompass: true
   },
   detailed: {
      aMessier: true, aCaldwell: true, aNamed: true, aNGC: true, aNGCMag: 14, aSNR: true,
      aBrightNeb: true, aDarkNeb: true, aPN: true, aClusters: true, aGalaxies: true, aGalaxyMag: 14,
      aGalaxyClusters: false,
      aStars: true, aStarMag: 6, aConstLines: true, aConstBorders: true, aConstNames: true,
      aGrid: true, aCompass: true
   },
   all: {
      aMessier: true, aCaldwell: true, aNamed: true, aNGC: true, aNGCMag: 20, aSNR: true,
      aBrightNeb: true, aDarkNeb: true, aPN: true, aClusters: true, aGalaxies: true, aGalaxyMag: 20,
      aGalaxyClusters: true,
      aStars: true, aStarMag: 10, aConstLines: true, aConstBorders: true, aConstNames: true,
      aGrid: true, aCompass: true
   }
};

class SceOptions
{
   constructor()
   {
      this.setDefaults();
   }

   setDefaults()
   {
      this.lang = "ja";
      this.title = "";
      this.observer = "";
      this.site = "";
      this.memo = "";
      this.pageSize = "A4";
      this.theme = "light";
      this.fontFamily = "";

      this.stretch = "auto";
      this.imageDpi = 200;
      this.autoCompress = true;
      this.maxSizeMB = 2.0;
      this.focalOverride = 0;
      this.pixelOverride = 0;
      // カメラ名 → 画素サイズ（µm）。手入力した値をカメラごとに覚えておく
      this.cameraPixelSizes = {};

      this.useStellarium = true;
      this.stellariumDir = "";
      this.useGaia = true;
      this.gaiaDir = "";

      this.annotPreset = "standard";
      this.applyPreset( "standard" );
      this.aLabelNames = true;
      this.aLabelSize = 6.5;

      this.chartFovMode = "auto";
      this.chartFovFactor = 8;
      this.chartFovDeg = 30;
      this.chartStarMag = 0;
      this.chartDSO = true;
      this.chartConstLines = true;
      this.chartConstBorders = true;
      this.chartConstNames = true;
      this.chartGrid = true;

      this.listMax = 40;
      this.listStars = true;

      this.outputDir = "";
   }

   applyPreset( name )
   {
      let p = SCE_PRESETS[name];
      if ( !p )
         return false;
      for ( let k in p )
         this[k] = p[k];
      this.annotPreset = name;
      return true;
   }

   // 現在の値に一致するプリセット名（なければ "custom"）
   matchingPreset()
   {
      for ( let name of SCE_PRESET_KEYS )
      {
         let p = SCE_PRESETS[name];
         let ok = true;
         for ( let k in p )
            if ( this[k] !== p[k] )
            {
               ok = false;
               break;
            }
         if ( ok )
            return name;
      }
      return "custom";
   }

   // 保存しない項目（画像ごとに変わるもの）
   static transientKeys()
   {
      // タイトル・メモ・焦点距離/画素サイズの手入力は画像ごとの内容なので次回に持ち越さない
      // （画素サイズはカメラ名ごとに cameraPixelSizes へ記憶する）
      return [ "title", "memo", "focalOverride", "pixelOverride" ];
   }

   load()
   {
      try
      {
         let s = Settings.read( SCE_SETTINGS_KEY, DataType.UTF16String );
         if ( Settings.lastReadOK && s )
         {
            let o = JSON.parse( s );
            for ( let k in o )
               if ( this.hasOwnProperty( k ) && typeof o[k] == typeof this[k] && SceOptions.transientKeys().indexOf( k ) < 0 &&
                    (o[k] !== null || this[k] === null) )
                  this[k] = o[k];
         }
      }
      catch ( x )
      {
         console.warningln( "SkyChartExporter: settings could not be loaded: " + x );
      }
      if ( this.lang != "ja" && this.lang != "en" )
         this.lang = "ja";
      if ( !SCE_PAGE_SIZES[this.pageSize] )
         this.pageSize = "A4";
   }

   save()
   {
      try
      {
         let o = {};
         for ( let k in this )
            if ( this.hasOwnProperty( k ) && SceOptions.transientKeys().indexOf( k ) < 0 )
               o[k] = this[k];
         Settings.write( SCE_SETTINGS_KEY, DataType.UTF16String, JSON.stringify( o ) );
      }
      catch ( x )
      {
         console.warningln( "SkyChartExporter: settings could not be saved: " + x );
      }
   }
}

#endif   // __SCE_Options_jsh
