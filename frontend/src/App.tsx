import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import Layout from './components/Layout';
import Login from './pages/Login';
import Register from './pages/Register';
import './App.css';

// Placeholder for the dashboard to be built in Chunk 2
const DashboardPlaceholder = () => (
  <div className="landing-hero">
    <h1>Dashboard Coming Soon</h1>
    <p>We're building something clean.</p>
  </div>
);

// Placeholder for the landing page
const LandingPlaceholder = () => (
  <div className="landing-hero">
    <h1>Welcome to OnyxShortener</h1>
    <p>Shorten, share, and track your links.</p>
  </div>
);

const App: React.FC = () => {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Layout />}>
            <Route index element={<LandingPlaceholder />} />
            <Route path="login" element={<Login />} />
            <Route path="register" element={<Register />} />
            <Route path="dashboard" element={<DashboardPlaceholder />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
};

export default App;
