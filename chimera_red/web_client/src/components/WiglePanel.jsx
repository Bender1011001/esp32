import React, { useState } from 'react';
import { Globe, Search, Target } from 'lucide-react';

export function WiglePanel({ gps, onTrack, onLog }) {
    const [wigleToken, setWigleToken] = useState(localStorage.getItem('wigleToken') || '');
    const [wigleResults, setWigleResults] = useState([]);
    const [wigleStatus, setWigleStatus] = useState('');

    const searchWigle = async () => {
        if (!wigleToken) {
            alert("Please enter your Wigle API Token (Encoded) from wigle.net/account");
            return;
        }

        setWigleStatus('Getting GPS...');

        const performSearch = async (latitude, longitude) => {
            setWigleStatus('Querying Wigle...');
            try {
                // Save token
                localStorage.setItem('wigleToken', wigleToken);

                // bounding box approx 1km
                const var_deg = 0.01;
                const latrange1 = latitude - var_deg;
                const latrange2 = latitude + var_deg;
                const longrange1 = longitude - var_deg;
                const longrange2 = longitude + var_deg;

                const query = `?latrange1=${latrange1}&latrange2=${latrange2}&longrange1=${longrange1}&longrange2=${longrange2}`;

                const res = await fetch(`https://api.wigle.net/api/v2/network/search${query}`, {
                    headers: {
                        'Authorization': `Basic ${wigleToken}`,
                        'Accept': 'application/json'
                    }
                });

                if (!res.ok) {
                    if (res.status === 401) throw new Error("Unauthorized - Check Token");
                    throw new Error(`API Error: ${res.status}`);
                }

                const data = await res.json();
                if (data.success && data.results) {
                    setWigleResults(data.results);
                    setWigleStatus(`Found ${data.results.length} networks nearby.`);
                    onLog(`Wigle: Found ${data.results.length} nets.`);
                } else {
                    setWigleStatus('No results or API error: ' + (data.message || 'Unknown'));
                }

            } catch (err) {
                console.error(err);
                setWigleStatus('Error: ' + err.message);
                onLog('Wigle Error: ' + err.message);
            }
        };

        if (gps) {
            performSearch(gps.latitude, gps.longitude);
        } else {
            navigator.geolocation.getCurrentPosition(
                (pos) => performSearch(pos.coords.latitude, pos.coords.longitude),
                (err) => setWigleStatus("GPS Error: " + err.message)
            );
        }
    };

    return (
        <div className="card glass">
            <div className="status-bar">
                <h2><Globe size={20} /> Wigle Intelligence</h2>
            </div>

            <div style={{ marginBottom: '20px', padding: '10px', background: 'rgba(255,255,255,0.05)', borderRadius: '8px' }}>
                <label style={{ display: 'block', marginBottom: '5px', fontSize: '0.9em', color: '#aaa' }}>
                    Wigle API Token (Encoded)
                    <a href="https://wigle.net/account" target="_blank" rel="noreferrer" style={{ marginLeft: '10px', color: 'var(--primary)' }}>Get Token</a>
                </label>
                <div style={{ display: 'flex', gap: '10px' }}>
                    <input
                        type="password"
                        value={wigleToken}
                        onChange={(e) => setWigleToken(e.target.value)}
                        placeholder="e.g. YOUR_ENCODED_TOKEN_HERE"
                        style={{ flex: 1, padding: '8px', borderRadius: '4px', border: '1px solid #444', background: '#222', color: '#fff' }}
                    />
                    <button className="btn" onClick={searchWigle} disabled={wigleStatus.startsWith('Querying') || wigleStatus.startsWith('Getting')}>
                        <Search size={16} style={{ marginRight: '5px' }} /> Search Area
                    </button>
                </div>
                {wigleStatus && <div style={{ marginTop: '10px', color: wigleStatus.includes('Error') ? '#f55' : '#0f0' }}>{wigleStatus}</div>}
            </div>

            <div className="list-container">
                {wigleResults.map((net, i) => (
                    <div key={i} className="list-item">
                        <div style={{ display: 'flex', flexDirection: 'column' }}>
                            <strong style={{ color: '#fff' }}>{net.ssid || '<Hidden>'}</strong>
                            <div style={{ fontSize: '0.8em', color: '#888' }}>
                                <span>{net.netid}</span>
                                <span style={{ margin: '0 8px' }}>|</span>
                                <span>{net.encryption}</span>
                                <span style={{ margin: '0 8px' }}>|</span>
                                <span>Type: {net.type}</span>
                            </div>
                        </div>
                        <div style={{ textAlign: 'right' }}>
                            <div className="badge">{new Date(net.lastupdt).toLocaleDateString()}</div>
                            <div style={{ fontSize: '0.7em', color: '#666', marginTop: '2px' }}>Last Seen</div>
                            <button className="btn btn-secondary" style={{ padding: '5px', marginTop: '5px' }} onClick={() => onTrack({ ssid: net.ssid, bssid: net.netid }, 'wifi')}>
                                <Target size={14} style={{ marginRight: '4px' }} /> Track
                            </button>
                        </div>
                    </div>
                ))}
                {wigleResults.length === 0 && !wigleStatus && (
                    <p style={{ textAlign: 'center', color: '#555', padding: '20px' }}>
                        Enter token and search to find nearby intelligence.
                    </p>
                )}
            </div>
        </div>
    );
}
