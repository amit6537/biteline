/*
 * Food photos from Unsplash (free to use, https://unsplash.com/license).
 * Every id below was checked to load and to actually show that dish.
 *
 * Two uses:
 *   1. The seed script and the owner forms save these URLs as imageUrl.
 *   2. When something has NO imageUrl, pickImage() guesses a fitting photo
 *      from its name / category / cuisine, so the app never looks empty.
 */

const unsplash = (id: string, w = 800) => `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=${w}&q=70`;

export const PHOTOS = {
  thaliSpread: '1585937421612-70a008356fbe',
  paneerMasala: '1631452180519-c014fe946bc7',
  butterChicken: '1603894584373-5ac82b2ae398',
  curryRice: '1588166524941-3bf61a9c41db',
  naanCurry: '1565557623262-b51c2513a641',
  paneerTikka: '1567188040759-fb8a883dc6d8',
  samosa: '1601050690597-df0568f70950',
  thali: '1626777552726-4a6b54c97e46',
  dal: '1596797038530-2c107229654b',
  tandoori: '1617692855027-33b14f061079',
  prawnCurry: '1559847844-5315695dadae',
  fishFillet: '1519708227418-c8fd9a32b7a2',
  fishPlated: '1580476262798-bddd9f4b7369',
  seafoodRaw: '1601314002592-b8734bca6604',
  bananaLeaf: '1625398407796-82650a8c135f',
  idliVada: '1630383249896-424e482df921',
  biryani: '1589302168068-964664d93dc0',
  tomatoRice: '1645177628172-a94c1f96e6db',
  pavBhaji: '1606491956689-2ea866880c84',
  kebabs: '1599487488170-d11ec9c172f0',
  muttonCurry: '1606843046080-45bf7a23c39f',
  pizzaBoard: '1565299624946-b28f40a0ae38',
  margherita: '1574071318508-1cdbab80d002',
  pepperoni: '1628840042765-356cda07504e',
  pizzaRustic: '1513104890138-7c749659a591',
  pizzaSlices: '1600628421055-4d30de868b8f',
  penne: '1621996346565-e3dbc646d9a9',
  noodles: '1563379926898-05f4575a45d8',
  chilliChicken: '1623689046286-01d812cc8bad',
  sushi: '1615361200141-f45040f367be',
  burger: '1568901346375-23c9450c58cd',
  ribs: '1544025162-d76694265947',
  steak: '1594041680534-e8c8cdebd659',
  meatballs: '1529042410759-befb1204b468',
  tiramisu: '1571877227200-a0d98ea607e9',
  iceCream: '1551024506-0bccd828d307',
  sweets: '1590080875515-8a3a8dc5735e',
  pancakes: '1567620905732-2d1ec7ab7445',
  powerBowl: '1546069901-ba9599a7e63c',
  buddhaBowl: '1512621776951-a57141f2eefd',
  saladDark: '1540189549336-e6e99c3679fe',
  saladBerry: '1505253716362-afaea1d3d1af',
  greenJuice: '1610970881699-44a5587cabec',
  meatBowls: '1504674900247-0877df9cc836',
  restaurantHall: '1517248135467-4c7edcad34c4',
  restaurantCafe: '1555396273-367ea4eb4db5',
  dining: '1414235077428-338989a2e8c0',
} as const;

export type PhotoKey = keyof typeof PHOTOS;

export function photoUrl(key: PhotoKey, w = 800) {
  return unsplash(PHOTOS[key], w);
}

/* Keyword → photo. First match wins, so put specific words before broad ones. */
const RULES: [RegExp, PhotoKey | PhotoKey[]][] = [
  [/pepperoni/, 'pepperoni'],
  [/margherita/, 'margherita'],
  [/farmhouse|veggie pizza/, 'pizzaRustic'],
  [/garlic bread|bruschetta/, 'pizzaSlices'],
  [/pizza|italian/, 'pizzaBoard'],
  [/penne|pasta|arrabbiata|spaghetti|lasagn/, 'penne'],
  [/tiramisu|cake|brownie|pastry/, 'tiramisu'],
  [/ice ?cream|sundae|kulfi|gulab|jamun|dessert|sweet/, 'iceCream'],
  [/pancake|waffle|breakfast/, 'pancakes'],
  [/biryani|pulao/, 'biryani'],
  [/jeera|fried rice|rice/, 'tomatoRice'],
  [/pav bhaji|bhaji|street/, 'pavBhaji'],
  [/samosa|pakora|chaat|snack/, 'samosa'],
  [/paneer tikka|tikka|sizzler/, 'paneerTikka'],
  [/paneer/, 'paneerMasala'],
  [/butter chicken|makhani chicken|murgh/, 'butterChicken'],
  [/tandoori|kebab|kabab|grill|bbq|barbecue/, 'kebabs'],
  [/dal|lentil/, 'dal'],
  [/naan|roti|paratha|kulcha|bread/, 'naanCurry'],
  [/mutton|lamb|rogan/, 'muttonCurry'],
  [/prawn|shrimp|gassi/, 'prawnCurry'],
  [/surmai|kingfish|salmon|fish fry|fish/, 'fishFillet'],
  [/bombil|pomfret|seafood|konkani|malvani|coastal/, 'fishPlated'],
  [/dosa|idli|vada|south indian|udupi/, 'idliVada'],
  [/kurma|korma|kadhi|curry|masala|gravy|mains/, 'curryRice'],
  [/thali|meal|combo/, 'thali'],
  [/noodle|chow|hakka|chinese|asian/, 'noodles'],
  [/manchurian|chilli|chili|schezwan/, 'chilliChicken'],
  [/sushi|japanese|ramen/, 'sushi'],
  [/burger|american|fast food|diner/, ['burger', 'ribs', 'steak', 'meatballs', 'kebabs']],
  [/smoke|smokehouse|bbq/, ['ribs', 'steak', 'kebabs', 'burger']],
  [/cafe|café|bistro|bakery/, 'pancakes'],
  [/ribs|steak|meat/, 'steak'],
  [/greek|feta|caesar/, 'saladDark'],
  [/salad/, 'saladBerry'],
  [/quinoa|power bowl|grain/, 'powerBowl'],
  [/chicken bowl|protein bowl/, 'meatBowls'],
  [/bowl|healthy|vegan|keto/, 'buddhaBowl'],
  [/juice|smoothie|shake|lassi|drink|cooler|beverage|soda|tea|coffee/, 'greenJuice'],
  [/north indian|punjabi|mughlai|indian|tiffin/, 'thaliSpread'],
];

/* No keyword matched → still give different things different photos. */
const POOL: PhotoKey[] = ['meatBowls', 'thaliSpread', 'curryRice', 'buddhaBowl', 'pizzaBoard', 'noodles', 'kebabs', 'naanCurry'];

function hash(text: string) {
  let h = 0;
  for (let i = 0; i < text.length; i++) h = (h * 31 + text.charCodeAt(i)) | 0;
  return Math.abs(h);
}

/**
 * Best photo for some free text (dish name, category, cuisine).
 * `seed` (e.g. the restaurant id) picks between several fitting photos, so
 * two places with the same kind of name don't look identical.
 */
export function pickImage(text: string | null | undefined, w = 800, seed?: string): string {
  const t = (text ?? '').toLowerCase();
  const h = hash(seed ?? t);
  for (const [re, key] of RULES) {
    if (re.test(t)) return photoUrl(Array.isArray(key) ? key[h % key.length]! : key, w);
  }
  return photoUrl(POOL[h % POOL.length]!, w);
}

/** Photos an owner can pick from in the restaurant / dish forms. */
export const GALLERY: PhotoKey[] = [
  'thaliSpread', 'paneerMasala', 'butterChicken', 'curryRice', 'naanCurry', 'paneerTikka', 'samosa', 'dal',
  'tandoori', 'biryani', 'pavBhaji', 'kebabs', 'muttonCurry', 'prawnCurry', 'fishFillet', 'seafoodRaw',
  'bananaLeaf', 'idliVada', 'pizzaBoard', 'margherita', 'pepperoni', 'penne', 'noodles', 'chilliChicken',
  'sushi', 'burger', 'ribs', 'powerBowl', 'buddhaBowl', 'saladDark', 'greenJuice', 'tiramisu', 'iceCream',
  'pancakes', 'restaurantHall', 'restaurantCafe', 'dining',
];
