const bcrypt = require('bcryptjs');
const crypto = require('crypto');
const User = require('../models/User');
const transporter = require('../mailer');
const { Op } = require('sequelize');
const jwt = require('jsonwebtoken');

exports.register = async (req, res) => {
    const { email, username, region, password } = req.body;

    try {

        const vToken = crypto.randomBytes(32).toString('hex');
        const hashedPassword = await bcrypt.hash(password, 10);

        const newUser = await User.create({
            email,
            username,
            region,
            password: hashedPassword,
            verificationToken: vToken
        });

        const verificationLink = `http://192.168.8.146:3000/api/auth/verify/${vToken}`;

        const mailOptions = {
            from: process.env.EMAIL_USER,
            to: email,
            subject: "Potvrda registracije - Slagalica",
            html: `
                <div style="font-family: Arial, sans-serif; border: 1px solid #ddd; padding: 20px;">
                    <h2>Dobrodošli na Slagalicu, ${username}!</h2>
                    <p>Hvala vam što ste se registrovali. Kliknite na dugme ispod da potvrdite nalog:</p>
                    <a href="${verificationLink}" 
                       style="background-color: #4CAF50; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">
                       Potvrdi nalog
                    </a>
                    <p>Ako dugme ne radi, kopirajte ovaj link u browser:</p>
                    <p>${verificationLink}</p>
                </div>
            `
        };

        await transporter.sendMail(mailOptions);

        res.status(201).json({ msg: "Registracija uspešna. Potvrdite mejl." });
    } catch (err) {
        console.log(err);
        res.status(500).json({ error: err.message });
    }
};

exports.verifyEmail = async (req, res) => {
    const user = await User.findOne({ where: { verificationToken: req.params.token } });
    if (!user) return res.status(400).send("Token neispravan.");

    await user.update({ isVerified: true, verificationToken: null });
    res.send("Email potvrđen!");
};

exports.login = async (req, res) => {
    const { identity, password } = req.body;

    const user = await User.findOne({
        where: {
            [Op.or]: [{ email: identity }, { username: identity }]
        }
    });

    if (!user) return res.status(404).send("Korisnik ne postoji.");
    if (!user.isVerified) return res.status(401).send("Nalog nije verifikovan.");

    const valid = await bcrypt.compare(password, user.password);
    if (!valid) return res.status(400).send("Pogrešna lozinka.");

    const token = jwt.sign(
        { id: user.id, username: user.username },
        process.env.JWT_SECRET,
        { expiresIn: '7d' }
    );

    res.json({
        msg: "Uspešan login!",
        token: token,
        user: {
            id: user.id,
            username: user.username,
            email: user.email
        }
    });
};

exports.resetPassword = async (req, res) => {
    const { oldPassword, newPassword } = req.body;
    try {
        const userId = req.user.id;

        const user = await User.findByPk(userId);
        const valid = await bcrypt.compare(oldPassword, user.password);
        if (!valid) return res.status(400).send("Stara lozinka je netačna.");

        const hashedNew = await bcrypt.hash(newPassword, 10);
        await user.update({ password: hashedNew });

        res.status(200).json({ msg: "Lozinka uspešno promenjena." });
    } catch (err) {
        console.error(err);
        res.status(500).json({ error: "Greška pri resetovanju lozinke." });
    }
};