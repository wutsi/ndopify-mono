-- Tenant 1 installs with the PWA icon set copied from Partner Central to the public site's assets.
UPDATE T_TENANT SET
    icon_192_url = 'https://com-wutsi-ndopify-test.s3.us-east-1.amazonaws.com/www/assets/images/icon-192.png',
    icon_512_url = 'https://com-wutsi-ndopify-test.s3.us-east-1.amazonaws.com/www/assets/images/icon-512.png',
    maskable_icon_url = 'https://com-wutsi-ndopify-test.s3.us-east-1.amazonaws.com/www/assets/images/icon-maskable-512.png',
    apple_touch_icon_url = 'https://com-wutsi-ndopify-test.s3.us-east-1.amazonaws.com/www/assets/images/apple-touch-icon.png'
WHERE id = 1;
