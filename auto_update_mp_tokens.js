const https = require('https');
const fs = require('fs');
const path = require('path');

const SECRETS_PATH = path.join(__dirname, 'secrets.properties');
const PRODUCTION_TOKEN = 'APP_USR-8056966599280604-033118-939fec54730128a17d14efe188c8fac7-3274574976';

function createTestUser() {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify({ site_id: 'MLC' });
    const options = {
      hostname: 'api.mercadopago.com',
      port: 443,
      path: '/users/test_user',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${PRODUCTION_TOKEN}`,
        'Content-Length': data.length
      }
    };

    const req = https.request(options, res => {
      let body = '';
      res.on('data', d => body += d);
      res.on('end', () => {
        try {
          const json = JSON.parse(body);
          if (json.access_token) resolve(json);
          else reject('Error API: ' + body);
        } catch (e) {
          reject('Error parsing: ' + body);
        }
      });
    });
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

async function main() {
  try {
    console.log('🚀 Solicitando nuevo usuario de prueba...');
    const testUser = await createTestUser();
    console.log('✅ Tokens obtenidos con éxito.');

    let content = fs.readFileSync(SECRETS_PATH, 'utf8');
    
    // Reemplazar tokens antiguos por los nuevos TEST-
    content = content.replace(/MP_ACCESS_TOKEN=.*/, `MP_ACCESS_TOKEN=${testUser.access_token}`);
    content = content.replace(/MP_PUBLIC_KEY=.*/, `MP_PUBLIC_KEY=${testUser.public_key}`);

    fs.writeFileSync(SECRETS_PATH, content, 'utf8');
    console.log('📝 secrets.properties actualizado con tokens TEST-.');
    console.log('\n--- NUEVOS TOKENS ---');
    console.log(`Public Key: ${testUser.public_key}`);
    console.log(`Access Token: ${testUser.access_token}`);
  } catch (err) {
    console.error('❌ ERROR FATAL:', err);
  }
}

main();
