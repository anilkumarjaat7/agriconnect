/**
 * Loads the Razorpay Checkout script on demand.
 *
 * <p>Pulling the SDK only when the buyer actually pays keeps the marketplace bundle free of a
 * third-party script. The promise is cached so repeated clicks do not inject duplicate tags, and
 * a failed load clears the cache so a retry can try again.
 */
let loadPromise = null;

export const loadRazorpayScript = () => {
  if (typeof window === 'undefined') {
    return Promise.reject(new Error('Razorpay Checkout can only load in a browser.'));
  }
  if (window.Razorpay) {
    return Promise.resolve(true);
  }
  if (loadPromise) {
    return loadPromise;
  }

  loadPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.async = true;
    script.onload = () => resolve(true);
    script.onerror = () => {
      loadPromise = null;
      script.remove();
      reject(new Error('Could not load Razorpay Checkout. Check your connection and try again.'));
    };
    document.body.appendChild(script);
  });

  return loadPromise;
};
