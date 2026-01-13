import React from 'react';
import { Activity } from 'lucide-react';

export function CSIPanel({ csiData, onStart, onStop }) {
    return (
        <div className="card glass">
            <div className="status-bar">
                <h2><Activity size={20} /> WiFi Sensing (CSI Radar)</h2>
                <div style={{ display: 'flex', gap: '10px' }}>
                    <button className="btn" onClick={onStart}>START</button>
                    <button className="btn btn-secondary" onClick={onStop}>STOP</button>
                </div>
            </div>
            <div style={{
                height: '300px',
                background: '#000',
                borderRadius: '8px',
                overflow: 'hidden',
                position: 'relative',
                border: '1px solid var(--border)'
            }}>
                {/* Simplified Waterfall Visualization */}
                {csiData.slice(-50).map((row, i) => (
                    <div key={i} style={{ display: 'flex', height: '6px', width: '100%', opacity: (i / 50) }} >
                        {row.data.map((amp, j) => {
                            // Subcarrier Heatmap
                            // Amp usually 0-127. Map to Color.
                            const intensity = Math.min(255, amp * 5); // Gain
                            const color = `rgb(${intensity}, ${255 - intensity}, 50)`;
                            return (
                                <div key={j} style={{ flex: 1, background: color }}></div>
                            )
                        })}
                    </div>
                ))}
                <div style={{ position: 'absolute', bottom: 10, left: 10, color: '#0f0', fontSize: '0.8em', background: 'rgba(0,0,0,0.5)', padding: '4px' }}>
                    Subcarriers (Frequency Domain) &rarr;
                </div>
            </div>
            <p style={{ fontSize: '0.8em', color: '#aaa', marginTop: '10px' }}>
                Visualizing multipath distortions (motion) in the environment.
                <br />Blue/Green = Steady state. Yellow/Red = Motion/Interference.
            </p>
        </div>
    );
}
