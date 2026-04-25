const express = require('express');
const sequelize = require('./db/db');
const authRoutes = require('./routes/authRoutes');
require('dotenv').config();

const app = express();

app.use(express.json()); 

// Rute
app.use('/api/auth', authRoutes);

const PORT = process.env.PORT || 3000;


sequelize.sync({ alter: true }) 
    .then(() => {
        console.log('Baza podataka je povezana i sinhronizovana.');
        app.listen(PORT, '0.0.0.0', () => {
            console.log(`Server radi na portu ${PORT}`);
        });
    })
    .catch(err => {
        console.error('Greška pri povezivanju sa bazom:', err);
    });

app.use((err, req, res, next) => {
    console.error("GLOBAL ERROR:", err);
    res.status(500).json({ error: "Something broke" });
});