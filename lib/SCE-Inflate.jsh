// ----------------------------------------------------------------------------
// SkyChartExporter - SCE-Inflate.jsh
//
// gzip（RFC 1952）/ deflate（RFC 1951）の展開。Stellarium の catalog.dat を
// 読むために使う。PixInsight の Compression オブジェクトは gzip ストリームを
// 直接扱えないため、JavaScript で実装している（zlib の puff.c と同じ方式）。
// ----------------------------------------------------------------------------

#ifndef __SCE_Inflate_jsh
#define __SCE_Inflate_jsh

var SCE_LEN_BASE  = [ 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258 ];
var SCE_LEN_EXTRA = [ 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0 ];
var SCE_DIST_BASE = [ 1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073,
                      4097, 6145, 8193, 12289, 16385, 24577 ];
var SCE_DIST_EXTRA = [ 0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13 ];
var SCE_CLEN_ORDER = [ 16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15 ];

/*
 * ハフマン符号表（正規ハフマン符号: 長さごとの個数と、長さ順に並べた記号）
 */
function sceHuffman( lengths, n )
{
   let count = new Uint16Array( 16 );
   let symbol = new Uint16Array( n );
   for ( let i = 0; i < n; ++i )
      ++count[lengths[i]];
   count[0] = 0;
   let offs = new Uint16Array( 16 );
   for ( let len = 1; len < 16; ++len )
      offs[len] = offs[len - 1] + count[len - 1];
   for ( let i = 0; i < n; ++i )
      if ( lengths[i] != 0 )
         symbol[offs[lengths[i]]++] = i;
   return { count: count, symbol: symbol };
}

/*
 * deflate ストリームを展開する。src[pos..] を読み、dst（十分な大きさの
 * Uint8Array）に書いて、書き込んだバイト数を返す。
 */
function sceInflateRaw( src, pos, dst )
{
   let bitBuf = 0, bitCnt = 0, out = 0;

   let bits = ( n ) =>
   {
      while ( bitCnt < n )
      {
         if ( pos >= src.length )
            throw new Error( "inflate: unexpected end of data" );
         bitBuf |= src[pos++] << bitCnt;
         bitCnt += 8;
      }
      let v = bitBuf & ((1 << n) - 1);
      bitBuf >>>= n;
      bitCnt -= n;
      return v;
   };

   let decode = ( h ) =>
   {
      let code = 0, first = 0, index = 0;
      for ( let len = 1; len < 16; ++len )
      {
         code |= bits( 1 );
         let count = h.count[len];
         if ( code - count < first )
            return h.symbol[index + (code - first)];
         index += count;
         first += count;
         first <<= 1;
         code <<= 1;
      }
      throw new Error( "inflate: invalid Huffman code" );
   };

   let fixedLit = null, fixedDist = null;
   let lengths = new Uint8Array( 320 );

   for ( ;; )
   {
      let last = bits( 1 );
      let type = bits( 2 );
      if ( type == 0 )
      {
         // 非圧縮ブロック: バイト境界にそろえる
         bitBuf = 0;
         bitCnt = 0;
         let len = src[pos] | (src[pos + 1] << 8);
         pos += 4;
         dst.set( src.subarray( pos, pos + len ), out );
         pos += len;
         out += len;
      }
      else
      {
         let lit, dist;
         if ( type == 1 )
         {
            if ( fixedLit === null )
            {
               let l = new Uint8Array( 288 );
               for ( let i = 0; i < 144; ++i ) l[i] = 8;
               for ( let i = 144; i < 256; ++i ) l[i] = 9;
               for ( let i = 256; i < 280; ++i ) l[i] = 7;
               for ( let i = 280; i < 288; ++i ) l[i] = 8;
               fixedLit = sceHuffman( l, 288 );
               let d = new Uint8Array( 30 );
               d.fill( 5 );
               fixedDist = sceHuffman( d, 30 );
            }
            lit = fixedLit;
            dist = fixedDist;
         }
         else if ( type == 2 )
         {
            let nlen = bits( 5 ) + 257, ndist = bits( 5 ) + 1, ncode = bits( 4 ) + 4;
            lengths.fill( 0 );
            for ( let i = 0; i < ncode; ++i )
               lengths[SCE_CLEN_ORDER[i]] = bits( 3 );
            let lencode = sceHuffman( lengths, 19 );
            lengths.fill( 0 );
            let index = 0;
            while ( index < nlen + ndist )
            {
               let sym = decode( lencode );
               if ( sym < 16 )
                  lengths[index++] = sym;
               else
               {
                  let len = 0, rep;
                  if ( sym == 16 )
                  {
                     if ( index == 0 )
                        throw new Error( "inflate: repeat with no previous length" );
                     len = lengths[index - 1];
                     rep = 3 + bits( 2 );
                  }
                  else if ( sym == 17 )
                     rep = 3 + bits( 3 );
                  else
                     rep = 11 + bits( 7 );
                  while ( rep-- > 0 )
                     lengths[index++] = len;
               }
            }
            lit = sceHuffman( lengths.subarray( 0, nlen ), nlen );
            dist = sceHuffman( lengths.subarray( nlen, nlen + ndist ), ndist );
         }
         else
            throw new Error( "inflate: invalid block type" );

         for ( ;; )
         {
            let sym = decode( lit );
            if ( sym < 256 )
               dst[out++] = sym;
            else if ( sym == 256 )
               break;
            else
            {
               sym -= 257;
               let len = SCE_LEN_BASE[sym] + bits( SCE_LEN_EXTRA[sym] );
               let ds = decode( dist );
               let d = SCE_DIST_BASE[ds] + bits( SCE_DIST_EXTRA[ds] );
               let from = out - d;
               if ( from < 0 )
                  throw new Error( "inflate: distance too far back" );
               // 重なりのある複写があるので1バイトずつ
               for ( let k = 0; k < len; ++k )
                  dst[out++] = dst[from + k];
            }
         }
      }
      if ( last )
         break;
   }
   return out;
}

/*
 * gzip データ（Uint8Array）を展開して Uint8Array を返す
 */
function sceGunzip( src )
{
   if ( src.length < 18 || src[0] != 0x1f || src[1] != 0x8b || src[2] != 8 )
      throw new Error( "Not a gzip stream" );
   let flg = src[3];
   let pos = 10;
   if ( flg & 4 )
      pos += 2 + (src[pos] | (src[pos + 1] << 8));
   if ( flg & 8 )
      while ( src[pos++] != 0 ) {}
   if ( flg & 16 )
      while ( src[pos++] != 0 ) {}
   if ( flg & 2 )
      pos += 2;
   let n = src.length;
   // 末尾4バイトが展開後のサイズ（mod 2^32）
   let isize = (src[n - 4] | (src[n - 3] << 8) | (src[n - 2] << 16) | (src[n - 1] << 24)) >>> 0;
   let dst = new Uint8Array( isize );
   let written = sceInflateRaw( src, pos, dst );
   return (written == isize) ? dst : dst.subarray( 0, written );
}

#endif   // __SCE_Inflate_jsh
