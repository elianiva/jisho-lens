// see: http://www.localizingjapan.com/blog/2012/01/20/regular-expressions-for-japanese-text/
const kHiraganaPattern = r'[\u3041-\u3096]';
const kKatakanaPattern = r'[\u30A0-\u30FF]';
const kKanjiPattern = r"[\u3400-\u4DB5\u4E00-\u9FCB\uF900-\uFA6A]";
const kMiscPattern = r'[\uFF00-\uFFEF]';
