const https = require('https');

const data = JSON.stringify({
  site_id: 'MLC' // Mercado Libre Chile
});

const options = {
  hostname: 'api.mercadopago.com',
  port: 443,
  path: '/users/test_user',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer APP_USR-8056966599280604-033118-939fec54730128a17d14efe188c8fac7-3274574976',
    'Content-Length': data.length
  }
};

const req = https.request(options, res => {
  let responseBody = '';

  res.on('data', d => {
    responseBody += d;
  });

  res.on('end', () => {
    console.log('--- RESULTADO ---');
    try {
      const json = JSON.parse(responseBody);
      if (json.access_token) {
        console.log('✅ USUARIO CREADO CON ÉXITO');
        console.log('PUBLIC_KEY: ' + json.public_key);
        console.log('ACCESS_TOKEN: ' + json.access_token);
      } else {
        console.log('❌ ERROR:', responseBody);
      }
    } catch (e) {
      console.log('❌ ERROR PARSING:', responseBody);
    }
  });
});

req.on('error', error => {
  console.error(error);
});

req.write(data);
req.end();
