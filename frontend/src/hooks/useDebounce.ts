import { useState, useEffect } from 'react';

/**
 * Custom hook for debouncing a value
 *
 * @param value - The value to debounce
 * @param delay - The delay in milliseconds (default: 1000ms)
 * @returns The debounced value
 *
 * @example
 * ```tsx
 * const [search, setSearch] = useState('');
 * const debouncedSearch = useDebounce(search, 1000);
 *
 * // debouncedSearch will only update 1 second after user stops typing
 * ```
 */
export function useDebounce<T>(value: T, delay: number = 1000): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);

  useEffect(() => {
    // Set up the timeout
    const timer = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    // Clean up the timeout if value changes before delay
    return () => {
      clearTimeout(timer);
    };
  }, [value, delay]);

  return debouncedValue;
}
