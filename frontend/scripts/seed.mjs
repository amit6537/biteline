#!/usr/bin/env node
/*
 * Demo data for local development.
 *   npm run seed            (backend must be running on VITE_API_URL or :8080)
 *
 * Creates (only once; running it again is safe):
 *   - owner     demo.owner@fooddelivery.local    / demo-password
 *   - customer  demo.customer@fooddelivery.local / demo-password
 *   - rider     demo.rider@fooddelivery.local    / demo-password  (online near the restaurants)
 *   - 6 restaurants in Mumbai with menus, all with food photos (Unsplash)
 *
 * Running it again also fills in photos for restaurants / dishes that were
 * created earlier without one (it never overwrites a photo you chose).
 */

const API = (process.env.VITE_API_URL || 'http://localhost:8080').replace(/\/$/, '');
const PASSWORD = 'demo-password';

const ACCOUNTS = {
  owner: { name: 'Demo Owner', email: 'demo.owner@fooddelivery.local', phone: '9000000001', role: 'RESTAURANT_OWNER' },
  customer: { name: 'Demo Customer', email: 'demo.customer@fooddelivery.local', phone: '9000000002', role: 'CUSTOMER' },
  rider: { name: 'Demo Rider', email: 'demo.rider@fooddelivery.local', phone: '9000000003', role: 'DELIVERY_PARTNER' },
};

// Unsplash photo ids (free to use) — same set as src/lib/images.ts
const img = (id, w = 800) => `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=${w}&q=70`;

// Around Bandra, Mumbai
const BASE = { lat: 19.0596, lng: 72.8295 };

const RESTAURANTS = [
  {
    name: 'Bombay Tiffin Co.',
    image: img('1585937421612-70a008356fbe', 1600),
    cuisineType: 'North Indian',
    description: 'Home-style thalis, slow-cooked dals and tandoor breads, made fresh every morning.',
    offset: [0.004, 0.002],
    avgPrepMinutes: 20,
    menu: [
      ['Paneer Butter Masala', 'Cottage cheese in a rich tomato and butter gravy.', 289, 'Mains', true, img('1631452180519-c014fe946bc7', 400)],
      ['Dal Makhani', 'Black lentils simmered overnight with cream.', 229, 'Mains', true, img('1596797038530-2c107229654b', 400)],
      ['Butter Chicken', 'Tandoori chicken in a velvety makhani sauce.', 329, 'Mains', false, img('1603894584373-5ac82b2ae398', 400)],
      ['Butter Naan', 'Soft tandoor bread brushed with butter.', 59, 'Breads', true, img('1565557623262-b51c2513a641', 400)],
      ['Garlic Naan', 'With roasted garlic and coriander.', 69, 'Breads', true, img('1617692855027-33b14f061079', 400)],
      ['Jeera Rice', 'Basmati tempered with cumin.', 149, 'Rice', true, img('1645177628172-a94c1f96e6db', 400)],
      ['Gulab Jamun (2 pcs)', 'Warm, soaked in cardamom syrup.', 99, 'Desserts', true, img('1551024506-0bccd828d307', 400)],
    ],
  },
  {
    name: 'Coastal Curry House',
    image: img('1601314002592-b8734bca6604', 1600),
    cuisineType: 'Seafood · Konkani',
    description: 'Malvani and Mangalorean seafood with fresh coconut masalas.',
    offset: [-0.006, 0.004],
    avgPrepMinutes: 25,
    minOrderAmount: 199,
    menu: [
      ['Prawn Gassi', 'Prawns in a tangy coconut and red-chilli curry.', 449, 'Curries', false, img('1559847844-5315695dadae', 400)],
      ['Surmai Fry', 'Kingfish steak, rava-crusted and pan-fried.', 399, 'Starters', false, img('1519708227418-c8fd9a32b7a2', 400)],
      ['Bombil Fry', 'Crispy Bombay duck.', 279, 'Starters', false, img('1580476262798-bddd9f4b7369', 400)],
      ['Vegetable Kurma', 'Seasonal vegetables in coconut gravy.', 249, 'Curries', true, img('1588166524941-3bf61a9c41db', 400)],
      ['Neer Dosa (4 pcs)', 'Soft, lacy rice crêpes.', 99, 'Breads', true, img('1625398407796-82650a8c135f', 400)],
      ['Sol Kadhi', 'Kokum and coconut milk cooler.', 79, 'Drinks', true, img('1610970881699-44a5587cabec', 400)],
    ],
  },
  {
    name: 'Slice Republic',
    image: img('1513104890138-7c749659a591', 1600),
    cuisineType: 'Pizza · Italian',
    description: 'Thin-crust pizzas from a wood-fired oven, plus fresh pasta.',
    offset: [0.008, -0.005],
    avgPrepMinutes: 18,
    menu: [
      ['Margherita', 'San Marzano tomato, fior di latte, basil.', 349, 'Pizza', true, img('1574071318508-1cdbab80d002', 400)],
      ['Farmhouse', 'Peppers, onion, mushroom, olives.', 429, 'Pizza', true, img('1565299624946-b28f40a0ae38', 400)],
      ['Pepperoni', 'Spicy pepperoni and mozzarella.', 499, 'Pizza', false, img('1628840042765-356cda07504e', 400)],
      ['Penne Arrabbiata', 'Garlic, chilli and tomato.', 319, 'Pasta', true, img('1621996346565-e3dbc646d9a9', 400)],
      ['Garlic Bread', 'With herb butter.', 149, 'Sides', true, img('1600628421055-4d30de868b8f', 400)],
      ['Tiramisu', 'Classic, with espresso and mascarpone.', 229, 'Desserts', true, img('1571877227200-a0d98ea607e9', 400)],
    ],
  },
  {
    name: 'Green Bowl',
    image: img('1512621776951-a57141f2eefd', 1600),
    cuisineType: 'Healthy · Salads',
    description: 'Grain bowls, salads and cold-pressed juices.',
    offset: [-0.003, -0.007],
    avgPrepMinutes: 12,
    menu: [
      ['Quinoa Power Bowl', 'Quinoa, chickpeas, roasted veggies, tahini.', 329, 'Bowls', true, img('1546069901-ba9599a7e63c', 400)],
      ['Grilled Chicken Bowl', 'Brown rice, chicken, greens, lemon dressing.', 379, 'Bowls', false, img('1504674900247-0877df9cc836', 400)],
      ['Greek Salad', 'Feta, olives, cucumber, tomato.', 279, 'Salads', true, img('1540189549336-e6e99c3679fe', 400)],
      ['Green Detox Juice', 'Spinach, apple, cucumber, ginger.', 169, 'Drinks', true, img('1610970881699-44a5587cabec', 400)],
    ],
  },
  {
    name: 'Hyderabad House',
    image: img('1589302168068-964664d93dc0', 1600),
    cuisineType: 'Biryani · Mughlai',
    description: 'Dum biryanis sealed and slow-cooked in handi, with kebabs from the grill.',
    offset: [0.002, 0.006],
    avgPrepMinutes: 25,
    minOrderAmount: 149,
    menu: [
      ['Chicken Dum Biryani', 'Long-grain basmati and chicken, slow-cooked under dough.', 349, 'Biryani', false, img('1589302168068-964664d93dc0', 400)],
      ['Veg Biryani', 'Seasonal vegetables, saffron and fried onions.', 269, 'Biryani', true, img('1645177628172-a94c1f96e6db', 400)],
      ['Mutton Rogan Josh', 'Kashmiri chilli, slow-braised lamb.', 429, 'Curries', false, img('1606843046080-45bf7a23c39f', 400)],
      ['Chicken Seekh Kebab', 'Minced chicken, charcoal-grilled on skewers.', 299, 'Kebabs', false, img('1599487488170-d11ec9c172f0', 400)],
      ['Paneer Tikka', 'Smoky tandoor paneer with peppers and onion.', 279, 'Kebabs', true, img('1567188040759-fb8a883dc6d8', 400)],
      ['Double Ka Meetha', 'Hyderabadi bread pudding.', 129, 'Desserts', true, img('1590080875515-8a3a8dc5735e', 400)],
    ],
  },
  {
    name: 'Mumbai Street Bites',
    image: img('1606491956689-2ea866880c84', 1600),
    cuisineType: 'Street Food · Snacks',
    description: 'Pav bhaji, samosas and chaat — the taste of Juhu beach, delivered hot.',
    offset: [-0.005, -0.002],
    avgPrepMinutes: 12,
    menu: [
      ['Pav Bhaji', 'Buttery mashed-vegetable bhaji with toasted pav.', 179, 'Street Food', true, img('1606491956689-2ea866880c84', 400)],
      ['Samosa (2 pcs)', 'Crisp pastry, spiced potato and peas, with chutney.', 69, 'Snacks', true, img('1601050690597-df0568f70950', 400)],
      ['Idli Vada Combo', 'Two idlis, one medu vada, sambar and chutneys.', 119, 'South Indian', true, img('1630383249896-424e482df921', 400)],
      ['Chilli Chicken', 'Indo-Chinese, wok-tossed with peppers.', 249, 'Indo-Chinese', false, img('1623689046286-01d812cc8bad', 400)],
      ['Hakka Noodles', 'Wok-tossed noodles with vegetables.', 199, 'Indo-Chinese', true, img('1563379926898-05f4575a45d8', 400)],
      ['Masala Lassi', 'Chilled, with roasted cumin.', 89, 'Drinks', true, img('1610970881699-44a5587cabec', 400)],
    ],
  },
];

async function call(method, path, body, token) {
  const res = await fetch(API + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;
  return { status: res.status, data };
}

async function ensureAccount(a) {
  const reg = await call('POST', '/api/auth/register', { ...a, password: PASSWORD });
  if (reg.status === 201) return reg.data.accessToken;
  const login = await call('POST', '/api/auth/login', { email: a.email, password: PASSWORD });
  if (login.status !== 200) {
    throw new Error(`Could not log in ${a.email}: ${login.status} ${login.data?.message ?? ''}`);
  }
  return login.data.accessToken;
}

/** Restaurant / dishes created before photos existed → add them (never overwrite one). */
async function backfillPhotos(existing, def, token) {
  let n = 0;
  if (!existing.imageUrl && def.image) {
    const res = await call('PATCH', `/api/restaurants/${existing.id}`, { imageUrl: def.image }, token);
    if (res.status === 200) n++;
  }
  const menu = await call('GET', `/api/restaurants/${existing.id}/menu`, null, token);
  for (const item of menu.data ?? []) {
    const match = def.menu.find(([name]) => name === item.name);
    if (!item.imageUrl && match?.[5]) {
      const res = await call('PATCH', `/api/restaurants/${existing.id}/menu/${item.id}`, { imageUrl: match[5] }, token);
      if (res.status === 200) n++;
    }
  }
  return n;
}

async function main() {
  const health = await call('GET', '/actuator/health').catch(() => null);
  if (!health || health.status !== 200) {
    throw new Error(`Backend not reachable at ${API}. Start it first (./mvnw spring-boot:run).`);
  }

  const owner = await ensureAccount(ACCOUNTS.owner);
  await ensureAccount(ACCOUNTS.customer);
  const rider = await ensureAccount(ACCOUNTS.rider);

  const mine = await call('GET', '/api/restaurants/my', null, owner);
  const existing = new Map((mine.data ?? []).map((r) => [r.name, r]));

  for (const r of RESTAURANTS) {
    const found = existing.get(r.name);
    if (found) {
      const added = await backfillPhotos(found, r, owner);
      console.log(`• ${r.name} already exists${added ? ` (added ${added} photos)` : ''}`);
      continue;
    }
    const created = await call(
      'POST',
      '/api/restaurants',
      {
        name: r.name,
        imageUrl: r.image,
        cuisineType: r.cuisineType,
        description: r.description,
        city: 'Mumbai',
        latitude: BASE.lat + r.offset[0],
        longitude: BASE.lng + r.offset[1],
        deliveryRadiusKm: 6,
        avgPrepMinutes: r.avgPrepMinutes,
        minOrderAmount: r.minOrderAmount ?? 0,
      },
      owner,
    );
    if (created.status !== 201) throw new Error(`Restaurant ${r.name}: ${created.status} ${created.data?.message}`);
    for (const [name, description, price, category, isVegetarian, imageUrl] of r.menu) {
      const item = await call('POST', `/api/restaurants/${created.data.id}/menu`, { name, description, price, category, isVegetarian, imageUrl }, owner);
      if (item.status !== 201) throw new Error(`Menu item ${name}: ${item.status} ${item.data?.message}`);
    }
    console.log(`✓ ${r.name} (${r.menu.length} dishes)`);
  }

  // Rider online in the middle of the area → orders get assigned automatically
  await call('PUT', '/api/delivery/status', { available: true, latitude: BASE.lat, longitude: BASE.lng }, rider);

  console.log(`
Done. Log in at http://localhost:5173 with:
  customer  ${ACCOUNTS.customer.email} / ${PASSWORD}
  owner     ${ACCOUNTS.owner.email} / ${PASSWORD}
  rider     ${ACCOUNTS.rider.email} / ${PASSWORD}
City: Mumbai. For "near me" or a delivery address, use a spot near ${BASE.lat}, ${BASE.lng}.`);
}

main().catch((e) => {
  console.error('Seed failed:', e.message);
  process.exit(1);
});
