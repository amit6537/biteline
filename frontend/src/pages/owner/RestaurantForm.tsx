import { useState, type FormEvent } from 'react';
import { LocateFixed } from 'lucide-react';
import { ApiError } from '@/api/client';
import type { Restaurant, RestaurantRequest } from '@/api/types';
import { useToast } from '@/components/Toast';
import { Button, Card, Input, Textarea } from '@/components/ui';
import { ImagePicker } from './ImagePicker';

/* Create AND edit form for a restaurant. City can only be set when creating (backend rule). */

// Bandra, Mumbai — same spot the demo data uses
const DEFAULT_SPOT = { lat: 19.0596, lng: 72.8295 };

interface Props {
  initial?: Restaurant;
  submitLabel: string;
  onSubmit: (body: RestaurantRequest) => Promise<unknown>;
}

export function RestaurantForm({ initial, submitLabel, onSubmit }: Props) {
  const toast = useToast();
  const [f, setF] = useState({
    name: initial?.name ?? '',
    cuisineType: initial?.cuisineType ?? '',
    description: initial?.description ?? '',
    city: initial?.city ?? 'Mumbai',
    latitude: String(initial?.latitude ?? DEFAULT_SPOT.lat),
    longitude: String(initial?.longitude ?? DEFAULT_SPOT.lng),
    deliveryRadiusKm: String(initial?.deliveryRadiusKm ?? 5),
    avgPrepMinutes: String(initial?.avgPrepMinutes ?? 20),
    minOrderAmount: String(initial?.minOrderAmount ?? 0),
    imageUrl: initial?.imageUrl ?? '',
  });
  const [busy, setBusy] = useState(false);
  const [locating, setLocating] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF((x) => ({ ...x, [k]: e.target.value }));

  function locate() {
    if (!navigator.geolocation) return toast.info('Your browser does not support location.');
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (p) => {
        setF((x) => ({ ...x, latitude: p.coords.latitude.toFixed(6), longitude: p.coords.longitude.toFixed(6) }));
        setLocating(false);
      },
      () => {
        setLocating(false);
        toast.info('Location permission denied. Type the coordinates instead.');
      },
      { timeout: 10_000 },
    );
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setErrors({});
    try {
      await onSubmit({
        name: f.name.trim(),
        cuisineType: f.cuisineType.trim() || undefined,
        description: f.description.trim() || undefined,
        city: f.city.trim(),
        latitude: Number(f.latitude),
        longitude: Number(f.longitude),
        deliveryRadiusKm: Number(f.deliveryRadiusKm),
        avgPrepMinutes: Number(f.avgPrepMinutes),
        minOrderAmount: Number(f.minOrderAmount),
        // editing: "" clears the photo (undefined would mean "don't change")
        imageUrl: initial ? f.imageUrl : f.imageUrl || undefined,
      });
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) setErrors(err.fieldErrors);
      else toast.error(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-6" noValidate>
      <Card className="space-y-4 p-5">
        <h2 className="text-base font-semibold">Basics</h2>
        <Input label="Restaurant name" value={f.name} onChange={set('name')} error={errors.name} placeholder="Bombay Tiffin Co." required />
        <Input label="Cuisine" value={f.cuisineType} onChange={set('cuisineType')} placeholder="North Indian · Biryani" hint="Shown under the name. Also used to pick a photo if you don't choose one." />
        <Textarea label="Description" value={f.description} onChange={set('description')} maxLength={500} placeholder="What makes your food special?" />
        <ImagePicker label="Cover photo" value={f.imageUrl} onChange={(imageUrl) => setF((x) => ({ ...x, imageUrl }))} name={f.name} hint={f.cuisineType} />
      </Card>

      <Card className="space-y-4 p-5">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-base font-semibold">Location</h2>
          <Button type="button" variant="secondary" size="sm" icon={LocateFixed} loading={locating} onClick={locate}>
            Use my location
          </Button>
        </div>
        <Input label="City" value={f.city} onChange={set('city')} error={errors.city} disabled={!!initial} hint={initial ? "City can't be changed after creating." : 'Customers browse restaurants by city.'} />
        <div className="grid grid-cols-2 gap-3">
          <Input label="Latitude" inputMode="decimal" value={f.latitude} onChange={set('latitude')} error={errors.latitude} />
          <Input label="Longitude" inputMode="decimal" value={f.longitude} onChange={set('longitude')} error={errors.longitude} />
        </div>
      </Card>

      <Card className="grid gap-4 p-5 sm:grid-cols-3">
        <h2 className="text-base font-semibold sm:col-span-3">Delivery</h2>
        <Input label="Delivery radius (km)" inputMode="decimal" value={f.deliveryRadiusKm} onChange={set('deliveryRadiusKm')} error={errors.deliveryRadiusKm} />
        <Input label="Avg. prep time (min)" inputMode="numeric" value={f.avgPrepMinutes} onChange={set('avgPrepMinutes')} error={errors.avgPrepMinutes} />
        <Input label="Minimum order (₹)" inputMode="decimal" value={f.minOrderAmount} onChange={set('minOrderAmount')} error={errors.minOrderAmount} />
      </Card>

      <Button type="submit" size="lg" loading={busy} disabled={!f.name.trim() || !f.city.trim()}>
        {submitLabel}
      </Button>
    </form>
  );
}
