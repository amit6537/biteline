import { useState } from 'react';
import { ImageIcon, Sparkles, X } from 'lucide-react';
import { Button, Input, Thumb } from '@/components/ui';
import { GALLERY, photoUrl } from '@/lib/images';
import { cn } from '@/lib/format';

/**
 * Choose a photo: pick one from the built-in Unsplash gallery, paste any
 * image URL, or leave it empty (the app then picks a fitting photo by name).
 */
export function ImagePicker({
  value,
  onChange,
  name,
  hint,
  label = 'Photo',
}: {
  value: string;
  onChange: (url: string) => void;
  name: string;
  hint?: string;
  label?: string;
}) {
  const [open, setOpen] = useState(false);

  return (
    <div>
      <span className="mb-1.5 block text-[13px] font-medium text-muted">{label}</span>
      <div className="flex gap-3">
        <Thumb src={value || null} name={name || 'dish'} hint={hint} width={300} className="size-20 shrink-0 rounded-md border border-line" />
        <div className="min-w-0 flex-1 space-y-2">
          <Input
            value={value}
            onChange={(e) => onChange(e.target.value.trim())}
            placeholder="https://… (or choose below)"
            aria-label="Image URL"
            hint={value ? undefined : 'Empty = we pick a matching photo automatically'}
          />
          <div className="flex gap-2">
            <Button type="button" variant="secondary" size="sm" icon={ImageIcon} onClick={() => setOpen((o) => !o)}>
              {open ? 'Hide gallery' : 'Choose from gallery'}
            </Button>
            {value && (
              <Button type="button" variant="ghost" size="sm" icon={X} onClick={() => onChange('')}>
                Remove
              </Button>
            )}
          </div>
        </div>
      </div>

      {open && (
        <div className="mt-3 grid grid-cols-5 gap-2 rounded-md border border-line bg-surface p-2 sm:grid-cols-8">
          <button
            type="button"
            onClick={() => {
              onChange('');
              setOpen(false);
            }}
            title="Pick automatically"
            className={cn('grid aspect-square place-items-center rounded-sm border text-muted hover:text-fg', !value ? 'border-fg' : 'border-line')}
          >
            <Sparkles size={18} />
          </button>
          {GALLERY.map((key) => {
            const url = photoUrl(key);
            return (
              <button
                type="button"
                key={key}
                title={key}
                onClick={() => {
                  onChange(url);
                  setOpen(false);
                }}
                className={cn('overflow-hidden rounded-sm border-2 transition-opacity hover:opacity-80', value === url ? 'border-fg' : 'border-transparent')}
              >
                <img src={photoUrl(key, 160)} alt={key} loading="lazy" className="aspect-square w-full object-cover" />
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
